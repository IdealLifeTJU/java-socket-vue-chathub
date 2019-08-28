package com.socketchat.demo;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint(value="/websocket/{nickName}")
@Component
public class MyWebSocket {
    //用来存放每个客户端对应的MyWebSocket对象。
    private static CopyOnWriteArraySet<MyWebSocket> webSocketSet = new CopyOnWriteArraySet<MyWebSocket>();
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;
    //客户端昵称
    private String nickName;
    //存储Session的集合
    private static Map<String, Session> map = new HashMap<String, Session>();
    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("nickName") String nickName) {
        this.session = session;
        this.nickName = nickName;

        map.put(session.getId(), session);
        webSocketSet.add(this);

        System.out.println("有新用户加入！当前在线人数为" + webSocketSet.size()+"。新用户名为："+this.nickName+"，新用户频道号为"+this.session.getId());
        String msg = getMessage(null,3);
        this.session.getAsyncRemote().sendText(msg);
        String msg1 = getMessage(null,2);
        broadcastExcludingThis(msg1);
    }
    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        webSocketSet.remove(this);  //从set中删除
        map.remove(this.session.getId());
        String msg = getMessage(null,4);
        broadcast(msg);
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
                broadcastExcludingThis(msg1);
                Thread.sleep(200);
            }catch(InterruptedException e) {
                e.printStackTrace();
            }
        }/*else if(header == 1){
            try{
                String msg = getMessage(null,9);
                this.session.getAsyncRemote().sendText(msg);
                String msg1 = getMessage(null,8);
                broadcastExcludingThis(msg1);
                Thread.sleep(200);
            }catch(InterruptedException e) {
                e.printStackTrace();
            }
        }*/
        byte[] temp = new byte[message.capacity()-1];
        message.get(temp,0,temp.length);
        ByteBuffer message1 = ByteBuffer.allocate(temp.length);
        message1.put(temp, 0, temp.length);
        message1.rewind();
        for (MyWebSocket item : webSocketSet) {
            item.session.getAsyncRemote().sendBinary(message1);//异步发送消息.
        }
    }
    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息*/
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("nickName") String nickName) {
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
                broadcastExcludingThis(msg1);
            }else if(socketMessage.getType() == 4){
                String msg = getMessage(socketMessage.getMsg(),9);
                this.session.getAsyncRemote().sendText(msg);
                String msg1 = getMessage(socketMessage.getMsg(),8);
                broadcastExcludingThis(msg1);
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
     * @param
     * @return String 返回类型：String
     */
    public String getMessage(String content, int type){
        JSONObject msg = new JSONObject();
        msg.put("type",type);
        msg.put("userNum",map.size());
        if(type==3){
            msg.put("content",this.session.getId());
        }else if(type==2 || type==4 || type==6){
            msg.put("nickName",this.nickName);
        }else if(type==1){
            msg.put("content",content);
            msg.put("nickName",this.nickName);
        }else if(type==5) {
            msg.put("content", content);
        }else if(type==7){
        }else if(type==8){
            msg.put("nickName",this.nickName);
            msg.put("fileName",content);
        }else if(type == 9){
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
}
