import exceptions.ChatException;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.util.stream.Collectors;


/**
 * Created by Kanes on 24.01.2017.
 */

@WebSocket
public class WebSocketHandler {

    private String sender, msg;
    private Chat chat = new Chat();

    private String createChannelCmd = "$Create_channel";
    private String switchChannelCmd = "$Change_forChannel";
    private String cmdDelimeter = "::";


    @OnWebSocketConnect
    public void onConnect(Session user) throws Exception {

        String username = "";

        try {
            username = user.getUpgradeRequest().getCookies().stream().filter(c -> c.getName().equals("username")).collect(Collectors.toList()).get(0).getValue();
        }catch(java.lang.IndexOutOfBoundsException e){
            log("Somebody tried to connect but problem with cookies qppeared.");
            user.close();
            return;
        }


        try {
            User newUser = new User(username, chat.getMainChannel(), user);

            chat.addUser(newUser);
            chat.updateSessionsInfo();
            chat.broadcastMessageAsServer(msg = (username + " joined the chat"), chat.getUsersChannel(username));
        }catch(ChatException e){
            log("Somebody tried to connect but user with this username already exists.");
            informAboutDisconnecting(user, "User already exists.");
            user.close();
        }
    }

    @OnWebSocketClose
    public void onClose(Session userSession, int statusCode, String reason) {
        try {
            User user = chat.getUser(userSession);
            chat.broadcastMessageAsServer(msg = (user.getUsername() + " left the chat"), chat.getUsersChannel(user.getUsername()));
            chat.removeUser(user);
            chat.updateSessionsInfo();
        }catch(ChatException e){
            log("Some problems appeared when somebody tried to disconnect. \n" + e.getMessage());
        }
    }

    @OnWebSocketMessage
    public void onMessage(Session user, String message) {
        try {
            if (isCreateChannel(message)) {

                chat.addChannel(new Channel(message.split(cmdDelimeter)[1]));
                chat.updateSessionsInfo();

            } else if (isSwitchChannels(message)) {

                chat.switchChannels(user, message.split(cmdDelimeter)[1]);

                chat.updateSessionsInfo();

            } else {

                chat.broadcastMessage(sender = chat.getUser(user).getUsername(), msg = message);

            }
        }catch(ChatException e){
            log("Problem appeared when communicating with server. \n" + e.getMessage());
            try {
                chat.broadcastMessageToUserAsServer("Channel already exists!", chat.getUser(user));
            }catch(ChatException f){
                log("Problem appeared when trying to tell user channel exists. \n" + e.getMessage());
            }
        }
    }



    private boolean isCreateChannel(String message) {
        return message.split(cmdDelimeter).length == 2 && message.split(cmdDelimeter)[0].equals(createChannelCmd);
    }

    private boolean isSwitchChannels(String message){
        return message.split(cmdDelimeter).length == 2 && message.split(cmdDelimeter)[0].equals(switchChannelCmd);
    }


    private void informAboutDisconnecting(Session user, String reason){
        try {
            user.getRemote().sendString(String.valueOf(new JSONObject()
                    .put("messageType", "youWontConnect")
                    .put("userMessage", "Dissconecting..." + reason)
            ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void log(String logmsg){
        System.out.println("    ------------------------------");
        System.out.println("[log] " + logmsg);
        System.out.println("    ------------------------------");
    }


    public void run(){
        chat.runChat();
    }

}
