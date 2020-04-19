package com.socketchat.demo;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
//import jdk.internal.net.http.common.SequentialScheduler;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint(value="/websocket/{roomNum}/{nickName}/{avatarNum}/{chosenHub}")
@Component
public class MyWebSocket {
    //用来存放每个客户端对应的MyWebSocket对象。
    private static CopyOnWriteArraySet<MyWebSocket> webSocketSet = new CopyOnWriteArraySet<MyWebSocket>();
    //公共房间总数
    private static int PUBLIC_ROOM_NUM = 6;
    //二人房间号偏移，为使预设的公共房间号不重合
    private static int ROOM_NUMBER_OFFSET = 100;
    //匿名二人房间号偏移，为使匿名房间与其他房间补充和
    private static int ANONYMOUS_ROOM_NUMBER_OFFSET = 10000;
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;
    //客户端昵称
    private String nickName;
    //聊天室编号
    private int roomNum;
    //客户端头像
    private int avatarNum;
    //客户端聊天室类型
    private int chosenHub;
    //按房间存储Session
    private static Map<Integer, Map<String, Session>> rooms = new HashMap<Integer, Map<String, Session>>();
    //匿名房间号集合
    private static LinkedList<Integer> anonymousRoomNum = new LinkedList<>();


    static {
        for(int i=0; i<PUBLIC_ROOM_NUM; i++){
            rooms.put(i, new HashMap<String, Session>());
        }
    }
    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("roomNum") int roomNum, @PathParam("nickName") String nickName, @PathParam("avatarNum") int avatarNum
    ,  @PathParam("chosenHub") int chosenHub) {
        this.session = session;
        this.nickName = nickName;
        this.avatarNum = avatarNum;
        this.chosenHub = chosenHub;
        this.roomNum = roomNum;


        if(chosenHub == 0){
            Map<String, Session> map = rooms.get(this.roomNum);
            map.put(session.getId(), session);
        }else if(chosenHub == 1){
            this.roomNum += ROOM_NUMBER_OFFSET;
            if(rooms.containsKey(this.roomNum)){
                Map<String, Session> map = rooms.get(this.roomNum);
                if(map.size()<2){
                    map.put(session.getId(), session);
                }else{
                    session.getAsyncRemote().sendText(getMessage(null,10));
                    return;
                }
            }else{
                Map<String, Session> map = new HashMap<>();
                map.put(session.getId(), session);
                this.rooms.put(this.roomNum, map);
            }
        }else if(chosenHub == 2){
            if(anonymousRoomNum.size() == 0){
                this.roomNum += ANONYMOUS_ROOM_NUMBER_OFFSET;
                Random random = new Random();
                int randomNum = random.nextInt();
                this.roomNum += randomNum;

                Map<String, Session> map = new HashMap<>();
                map.put(this.session.getId(), this.session);
                rooms.put(this.roomNum, map);
                anonymousRoomNum.addLast(this.roomNum);
            }else{
                int roomId = anonymousRoomNum.removeFirst();
                Map<String, Session> map = rooms.get(roomId);
                map.put(this.session.getId(), this.session);
                this.roomNum = roomId;
            }
        }

        webSocketSet.add(this);
        System.out.println("有新用户加入！当前在线人数为" + webSocketSet.size()+"。新用户名为："+this.nickName+"，新用户房间号为"+this.roomNum);
        String msg = getMessage(null,3);
        this.session.getAsyncRemote().sendText(msg);
        String msg1 = getMessage(null,2);
        broadcastExcludingThis(msg1, this.roomNum);
    }
    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        webSocketSet.remove(this);  //从set中删除
        Map<String, Session> map = rooms.get(this.roomNum);
        map.remove(session.getId());
        String msg = getMessage(null,4);
        broadcast(msg, this.roomNum);
        System.out.println("用户"+this.session.getId()+"退出！当前在线人数为" + webSocketSet.size());
    }

    //用于接收文件（包括图片）的字节流数据
    @OnMessage(maxMessageSize=100000000)
    public void OnMessage(ByteBuffer message){
        //发送文件信息时，先向各客户端发送一个头信息，包含客户端处理数据所需的一些数据
        //随后再向各个客户端发送二进制字节流，客户端将根据之前所收到的头信息处理这些文件

        System.out.println("有客户端发送了文件：");
        System.out.println(message);
        int header = message.get();
        System.out.println(header);
        if(header == 2){
            try{
                String msg = getMessage(null,7);
                this.session.getAsyncRemote().sendText(msg);
                String msg1 = getMessage(null,6);
                broadcastExcludingThis(msg1, this.roomNum);
                Thread.sleep(200);
            }catch(InterruptedException e) {
                e.printStackTrace();
            }
        }

        byte[] temp = new byte[message.capacity()-1];
        message.get(temp,0,temp.length);
        ByteBuffer message1 = ByteBuffer.allocate(temp.length);
        message1.put(temp, 0, temp.length);
        message1.rewind();
        broadcast(message1, this.roomNum);
    }
    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息*/
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("nickName") String nickName) {
        //首先查看是否心跳检测
        if(message.equals("123456789")){
            this.session.getAsyncRemote().sendText("123456789");
            return;
        }
        System.out.println("来自" + session.getId() + "的消息："+ message);
        System.out.println(this.session.getId() + " " + this.nickName);

        //从客户端传过来的数据是json数据，所以这里使用jackson进行转换为SocketMsg对象，
        // 然后通过socketMsg的type进行判断是单聊还是群聊，进行相应的处理:
        ObjectMapper objectMapper = new ObjectMapper();
        SocketMessage socketMessage;
        try{
            socketMessage = objectMapper.readValue(message,SocketMessage.class);
            if(socketMessage.getType() == 1){
                String msg = getMessage(socketMessage.getMsg(),5);
                this.session.getAsyncRemote().sendText(msg);
                String msg1 = getMessage(socketMessage.getMsg(),1);
                broadcastExcludingThis(msg1, this.roomNum);
            }else if(socketMessage.getType() == 4){
                String msg = getMessage(socketMessage.getMsg(),9);
                this.session.getAsyncRemote().sendText(msg);
                String msg1 = getMessage(socketMessage.getMsg(),8);
                broadcastExcludingThis(msg1, this.roomNum);
            }
        }catch(JsonParseException e){
            e.printStackTrace();
        }catch(IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * 组装返回给前台的信息
     * @param type 信息类型：1 服务器转发的来自于其他客户端的文字信息
     *                     2 有新用户连接进来向其他客户端发送的系统信息
     *                     3 连接成功之后向对应客户端发送的回显系统信息
     *                     4 有用户断开连接之后向其他客户端发送的系统信息
     *                     5 用户向服务器发送信息之后回显的文字信息
     *                     6 服务器转发的来自于其他客户端的图片数据，只包含头信息，数据随后发送
     *                     7 用户向服务器发送信息之后回显的图片数据，只包含头信息，数据随后发送
     *                     8 服务器转发的来自于其他客户端的文件数据，只包含头信息，数据随后发送
     *                     9 用户向服务器发送文件之后回显的文件数据，只包含头信息，数据随后发送
     *                     10 服务器向客户端回显的连接失败信息
     * @param
     * @return String 返回类型：String
     */
    public String getMessage(String content, int type){
        JSONObject msg = new JSONObject();
        msg.put("type",type);
        msg.put("userNum", rooms.get(this.roomNum).size());
        msg.put("nickName", this.nickName);
        msg.put("avatarNum", this.avatarNum);
        if(type==3 || type==10) {
            msg.put("content", this.session.getId());
        }else if(type==1 || type == 5){
            msg.put("content",content);
        }else if(type == 8 || type == 9){
            msg.put("fileName",content);
        }
        return msg.toString();
    }
    /**
     * 发生错误时调用
     *
     */
    @OnError
    public void onError(Session session, Throwable error) {
        System.out.println("发生错误");
        error.printStackTrace();
    }
    /**
     * 群发自定义消息
     * */
    public  void broadcast(String message){
        for (MyWebSocket item : webSocketSet) {
            //同步异步说明参考：http://blog.csdn.net/who_is_xiaoming/article/details/53287691
            //this.session.getBasicRemote().sendText(message);
            item.session.getAsyncRemote().sendText(message);//异步发送消息.
        }
    }

    /**
     * 群发，除了此WebSocket对应的客户端以外
     * @param message
     */
    public void broadcastExcludingThis(String message){
        for(MyWebSocket item: webSocketSet){
            if(!item.session.getId().equals(this.session.getId())){
                item.session.getAsyncRemote().sendText(message);
            }
        }
    }

    /**
     * 按照房间号群发
     * @param message
     */
    public void broadcast(String message, int roomNum){
        Map<String, Session> map = rooms.get(roomNum);
        for(MyWebSocket item: webSocketSet){
            if(map.containsKey(item.session.getId())){
                item.session.getAsyncRemote().sendText(message);
            }
        }
    }

    /**
     * 按照房间号群发，但自己客户端不发
     * @param message
     */
    public void broadcastExcludingThis(String message, int roomNum){
        Map<String, Session> map = rooms.get(roomNum);
        for(MyWebSocket item: webSocketSet){
            if(map.containsKey(item.session.getId()) && item.session.getId() != session.getId()){
                item.session.getAsyncRemote().sendText(message);
            }
        }
    }

    /**
     * 按照房间号群发二进制数据
     * @param message
     */
    public void broadcast(ByteBuffer message, int roomNum){
        Map<String, Session> map = rooms.get(roomNum);
        for(MyWebSocket item: webSocketSet){
            if(map.containsKey(item.session.getId())){
                item.session.getAsyncRemote().sendBinary(message);
            }
        }
    }
}
