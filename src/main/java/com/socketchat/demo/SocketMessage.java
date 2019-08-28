package com.socketchat.demo;

import java.nio.ByteBuffer;

public class SocketMessage {
    /*客户端所传输的信息类型：1. 文字信息
                           2. 图片
                           3. 除图片以外的其他文件
    */
    private int type;
    private String msg;
    private ByteBuffer fileStream;
    public int getType(){
        return type;
    }
    public void setType(int type) {
        this.type = type;
    }
    public String getMsg() {
        return msg;
    }
    public void setMsg(String msg) {
        this.msg = msg;
    }
}
