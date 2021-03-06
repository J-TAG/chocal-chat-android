package org.puresoftware.chocalandroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * org.puresoftware.chocalandroid
 * Created by Hesam Gholami on 2016/03/22 18:56.
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final int PICK_ATTACHMENT_PHOTO = 3;
    private static final int CAPTURE_ATTACHMENT_PHOTO = 12;


    private Bitmap mAttachmentPhoto;
    private static ListView mChatView;
    private ChatAdapter mAdapter;

    private Button mBtnSend;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Set current Chocal activity
        Chocal.setActivity(MainActivity.this);

        // Set name and status in navigation pane header
        View headerView = navigationView.getHeaderView(0);
        TextView nameView = (TextView) headerView.findViewById(R.id.nav_name);
        TextView statusView = (TextView) headerView.findViewById(R.id.nav_status);

        nameView.setText(Chocal.getCurrentUser().name);
        statusView.setText(R.string.online);

        // Show user Avatar as a circular image
        ImageView avatar = (ImageView) headerView.findViewById(R.id.nav_avatar);
        ImageView chatAvatar = (ImageView) findViewById(R.id.img_avatar);
        avatar.setImageDrawable(Chocal.getCurrentUser().getAvatarDrawable(this));
        chatAvatar.setImageDrawable(Chocal.getCurrentUser().getAvatarDrawable(this));

        // Handle send button
        mBtnSend = (Button) findViewById(R.id.btn_send);
        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });

        // Handle attach button
        final ImageButton btnAttach = (ImageButton) findViewById(R.id.img_btn_attachment);
        btnAttach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAttachment();
            }
        });

        // Handle message edit text
        final EditText txtMessage = (EditText) findViewById(R.id.txt_message);
        txtMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Show send button whenever there is any text in edit text
                int textLength = txtMessage.length();
                if (textLength == 0) {
                    hideSendButton();
                } else {
                    showSendButton();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // Load message view
        mAdapter = new ChatAdapter(this);
        mChatView = (ListView) findViewById(R.id.chat_list_view);
        mChatView.setAdapter(mAdapter);

        // Refresh view
        refreshTitle();
        refreshMessageView();
    }

    public void showSendButton() {
        if (mBtnSend.getTag() == null) {
            final Animation scaleUp = AnimationUtils.loadAnimation(MainActivity.this,
                    R.anim.scale_up);
            mBtnSend.startAnimation(scaleUp);
            mBtnSend.setTag(true);
        }
    }

    public void hideSendButton() {
        final Animation scaleDown = AnimationUtils.loadAnimation(MainActivity.this,
                R.anim.scale_down);
        mBtnSend.startAnimation(scaleDown);
        mBtnSend.setTag(null);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
            // Leave chat
            leave();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_online_users) {
            // Show users list
            Intent intent = new Intent(this, UsersActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_leave_chat) {
            // Leave chat
            leave();
        } else if (id == R.id.nav_about) {
            // Show about page
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public synchronized void refreshTitle() {
        // Show number of online users as title
        String title = getString(R.string.online_number);
        setTitle(String.format(title, Chocal.getUsers().size()));
    }

    public synchronized void refreshMessageView() {
        // Update message list
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                mAdapter.notifyDataSetChanged();
                goToLast();
            }
        });
    }

    public void goToLast() {
        mChatView.post(new Runnable() {
            @Override
            public void run() {
                // Select last row
                mChatView.smoothScrollToPosition(mAdapter.getCount() - 1);
            }
        });
    }

    /**
     * This method will triggered when send button is clicked.
     */
    private void send() {
        EditText txtMessage = (EditText) findViewById(R.id.txt_message);
        String strMessage = txtMessage.getText().toString();

        // Decide to send plain message or image message
        if (mAttachmentPhoto == null) {
            if (!strMessage.trim().isEmpty()) {
                Chocal.sendTextMessage(strMessage);
            }
        } else {
            Chocal.sendImageMessage(strMessage, mAttachmentPhoto);
            mAttachmentPhoto = null;
        }

        // Empty edit text
        txtMessage.setText("");
        txtMessage.requestFocus();
    }

    private void selectAttachment() {
        final Dialog dlgAttachment = new Dialog(this);

        dlgAttachment.setContentView(R.layout.dialog_attachment);
        dlgAttachment.setTitle(getString(R.string.select_attachment_photo));

        // Handle capture button
        final Button btnCamera = (Button) dlgAttachment.findViewById(R.id.capture_photo_button);
        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturePhoto();
                dlgAttachment.dismiss();
            }
        });

        // Handle choose button
        final Button btnChoose = (Button) dlgAttachment.findViewById(R.id.choose_photo_button);
        btnChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePhoto();
                dlgAttachment.dismiss();
            }
        });

        // Show attachment dialog
        dlgAttachment.show();
    }

    private void choosePhoto() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_photo)),
                PICK_ATTACHMENT_PHOTO);
    }

    private void capturePhoto() {
        Intent intent = new Intent();
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.take_photo)),
                CAPTURE_ATTACHMENT_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_ATTACHMENT_PHOTO && resultCode == Activity.RESULT_OK) {
            // User selected photo from gallery

            try {
                // Get the returned data
                InputStream inputStream = getContentResolver().openInputStream(data.getData());
                // Update attachment photo
                mAttachmentPhoto = BitmapFactory.decodeStream(inputStream);
                // Show snack bar
                Snackbar.make(Chocal.getActivity().findViewById(R.id.btn_send),
                        R.string.attachment_photo_added, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                // Show send button
                showSendButton();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        } else if (requestCode == CAPTURE_ATTACHMENT_PHOTO && resultCode == Activity.RESULT_OK) {
            // User captured photo from camera

            // Get the returned data
            Bundle extras = data.getExtras();
            // Update attachment photo
            mAttachmentPhoto = extras.getParcelable("data");

            // Show snack bar
            Snackbar.make(Chocal.getActivity().findViewById(R.id.btn_send),
                    R.string.attachment_photo_added, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // Show send button
            showSendButton();
        }
    }

    private void leave() {
        // Disconnect from Chocal Server and leave chat
        Chocal.leave();
        // Show toast to note user that he left the chat
        Toast.makeText(this, R.string.you_left_chat_successfully, Toast.LENGTH_LONG).show();
        // Close current intent
        finish();
        // Show join form
        Intent intent = new Intent(this, JoinActivity.class);
        startActivity(intent);
    }
}
