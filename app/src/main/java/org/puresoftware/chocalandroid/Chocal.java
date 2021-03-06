package org.puresoftware.chocalandroid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Main handler class for communicate with Chocal Server
 */
public class Chocal {

    private static final Chocal _instance = new Chocal();
    private static final WebSocketFactory mSocketFactory = new WebSocketFactory();
    public static final String TAG_CHOCAL_SOCKET = "Chocal.Socket";
    private static WebSocket mConnection;
    private static String mUri;
    private static User mUser = new User();
    private static AppCompatActivity mActivity;
    private static ArrayList<User> mUsers = new ArrayList<>();
    private static String mUserKey;
    private static ArrayList<IMessage> mMessages = new ArrayList<>();

    private Chocal() {

    }

    /**
     * Note: This method will triggered by an Async task from JoinActivity class in background thread. Keep in mind that this method will not have access to UI components.
     * @return boolean
     */
    public static synchronized boolean initWebSocket() {
        // Create a web socket. The scheme part can be one of the following:
        // 'ws', 'wss', 'http' and 'https' (case-insensitive). The user info
        // part, if any, is interpreted as expected. If a raw socket failed
        // to be created, an IOException is thrown.
        try {
            mConnection = mSocketFactory.createSocket(mUri);

            // Register a listener to receive web socket events.
            mConnection.addListener(new ChocalWebSocketAdapter());

            // Connect to the server and perform an opening handshake.
            // This method blocks until the opening handshake is finished.
            mConnection.connect();

            // Everything goes well, so return true
            return true;

        } catch (IOException | WebSocketException e) {
            e.printStackTrace();
            Log.e(TAG_CHOCAL_SOCKET, "Can't connect to Chocal Server. " + e.toString());
            // Something went wrong, so return false
            return false;
        }

    }

    /**
     * Add all currently online clients to users list.
     * @param json Received JSON message from server.
     */
    public static synchronized void initOnlineUsers(JSONObject json) {

        // First of all add current user
        mUsers.add(mUser);

        try {
            // Store user key
            mUserKey = json.getString("user_key");

            // Get online users
            JSONArray onlineClients = json.getJSONArray("online_users");

            for(int i = 0 ; i < onlineClients.length(); i++){
                JSONObject client = onlineClients.getJSONObject(i);
                addUser(client);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public static synchronized void sendRegisterMessage() {
        // Try to send register request message
        JSONObject register = new JSONObject();
        String strJson;

        try {
            register.put("type", "register");
            register.put("name", mUser.name);
            if (mUser.avatar != null) {
                register.put("image", base64Encode(mUser.avatar, Bitmap.CompressFormat.JPEG, 100));
                register.put("image_type", "jpeg");
            }

            strJson = register.toString();
            mConnection.sendText(strJson);
            Log.d(TAG_CHOCAL_SOCKET, "Sending register request message: " + strJson);

        } catch (JSONException e) {
            e.printStackTrace();
            showError(mActivity.getString(R.string.cant_connect_to_chocal_chat));
        }
    }

    protected static synchronized void sendGeneralMessage(String message, Bitmap image) {
        JSONObject json = new JSONObject();
        String strJson;

        try {
            if (image == null) {
                json.put("type", "plain");
            } else {
                json.put("type", "image");
                json.put("image", Chocal.base64Encode(image, Bitmap.CompressFormat.JPEG, 100));
                json.put("image_type", "jpeg");
            }

            json.put("user_key", mUserKey);
            json.put("message", message);

            strJson = json.toString();
            mConnection.sendText(json.toString());
            Log.d(TAG_CHOCAL_SOCKET, "Sending message: " + strJson);

        } catch (JSONException e) {
            e.printStackTrace();
            showError(mActivity.getString(R.string.message_wasnt_send));
        }
    }

    public static synchronized void sendTextMessage(String message) {
        sendGeneralMessage(message, null);
    }

    public static synchronized void sendImageMessage(String message, Bitmap image) {
        sendGeneralMessage(message, image);
    }

    protected static synchronized void appendGeneralMessage(JSONObject json) {
        try {
            String strType = json.getString("type");
            IMessage message;

            switch (strType) {
                case "plain":
                    message = new PlainMessage(getUser(json.getString("name")),
                            json.getString("message"));
                    break;
                case "image":
                    message = new ImageMessage(getUser(json.getString("name")),
                            json.getString("message"), base64Decode(json.getString("image")));
                    break;
                case "info":
                    message = new InfoMessage(json.getString("message"));
                    break;
                default:
                    throw new Exception("Message type is not handled.");
            }

            // Check to see if sender user is current user
            message.setIsSelfMessage(message.getUser().name.equals(mUser.name));

            mMessages.add(message);
            refreshMessageView();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void appendTextMessage(JSONObject json) {
        appendGeneralMessage(json);
    }

    public static synchronized void appendImageMessage(JSONObject json) {
        appendGeneralMessage(json);
    }

    public static synchronized void appendInfoMessage(JSONObject json) {
        appendGeneralMessage(json);
    }

    public static synchronized void appendErrorMessage(JSONObject json) {
        String strError;
        try {
            strError = json.getString("message");
        } catch (JSONException e) {
            e.printStackTrace();
            strError = mActivity
                    .getString(R.string.an_error_occurred_while_trying_to_show_another_error);
        }

        showError(strError);
    }

    public static synchronized void showError(String error) {
        if(mActivity instanceof MainActivity) {
            Snackbar.make(mActivity.findViewById(R.id.btn_send), error, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    public static synchronized void leave() {
        // Disconnect socket
        disconnect();
        // Destroy old data
        setCurrentUser(null);
        setUri(null);
        mUsers.clear();
        mMessages.clear();
        // Reload new data
        User.resetCounter();
        setCurrentUser(new User());
        PlainMessage.resetCounter();
    }

    public static User getCurrentUser() {
        return mUser;
    }

    public static void setCurrentUser(User mUser) {
        Chocal.mUser = mUser;
    }

    public static synchronized ArrayList<User> getUsers() {
        return mUsers;
    }

    public static synchronized User getUser(int index){
        return mUsers.get(index);
    }

    public static synchronized User getUser(String name) {
        return getUser(getUserIndexByName(name));
    }

    public static synchronized void addUser(JSONObject json) throws JSONException {
        String name = json.getString("name");

        // Don't add current user, because it was added before
        if (name.equals(mUser.name)) {
            return;
        }

        Bitmap avatar = null;
        if(json.has("image")) {
            avatar = base64Decode(json.getString("image"));
        }
        User user = new User(name, avatar);
        mUsers.add(user);
    }

    public static synchronized void removeUser(String name) {
        mUsers.remove(getUserIndexByName(name));
    }

    public static synchronized int getUserIndexByName(String name) {
        for (int i = 0; i < mUsers.size(); i++) {
            User user = mUsers.get(i);
            if (user.name.equals(name)) {
                return i;
            }
        }
        // Name not found
        return -1;
    }

    public static synchronized ArrayList<IMessage> getMessages() {
        return mMessages;
    }

    public static synchronized void showMainActivity() {
        // Go to main activity
        Intent intent = new Intent(mActivity, MainActivity.class);
        mActivity.startActivity(intent);
        mActivity.finish();
    }

    public static synchronized void showProgress(boolean bShow) {
        if(mActivity instanceof JoinActivity) {
            ((JoinActivity) mActivity).showProgress(bShow);
        }
    }

    public static synchronized void refreshOnlineUsers() {
        if (mActivity instanceof MainActivity) {
            ((MainActivity)mActivity).refreshTitle();
        }

        if (mActivity instanceof UsersActivity) {
            ((UsersActivity)mActivity).refreshOnlineUsers();
        }
    }

    public static synchronized void refreshMessageView() {
        if (mActivity instanceof MainActivity) {
            ((MainActivity)mActivity).refreshMessageView();
        }
    }

    public static synchronized void disconnect() {
        mConnection.disconnect();
    }

    public static String base64Encode(Bitmap image, Bitmap.CompressFormat compressFormat, int quality)
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        image.compress(compressFormat, quality, byteArrayOutputStream);
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
    }

    public static Bitmap base64Decode(String input)
    {
        byte[] decodedBytes = Base64.decode(input, 0);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    /**
     * @return Chocal
     */
    public static synchronized Chocal getInstance() {
        return _instance;
    }

    public static synchronized Activity getActivity() {
        return mActivity;
    }

    public static synchronized void setActivity(AppCompatActivity activity) {
        Chocal.mActivity = activity;
    }

    public static synchronized String getUri() {
        return mUri;
    }

    public static synchronized void setUri(String mUri) {
        Chocal.mUri = mUri;
    }

    public static synchronized WebSocket getConnection() {
        return mConnection;
    }
}
