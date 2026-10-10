// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.mediarecipient;

import android.app.Activity;
import android.content.ClipDescription;
import android.database.Cursor;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.OsConstants;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate APK/UID/task. Receives actual IME content grants, never shares Obadh code or data. */
public final class MediaRecipientActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;
    private ImageView preview;
    private int resumes;
    @Override public void onCreate(Bundle state) {
        boolean dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(24,96,24,24);
        TextView title=new TextView(this);title.setText("Independent media recipient");title.setTextSize(20);
        body.addView(title);
        EditText editor=new EditText(this) {
            @Override public InputConnection onCreateInputConnection(EditorInfo info) {
                InputConnection base=super.onCreateInputConnection(info);
                String types=getIntent().getStringExtra("types");
                info.contentMimeTypes=(types==null?"image/gif,image/webp":types).split(",");
                return new InputConnectionWrapper(base,false) {
                    @Override public boolean commitContent(InputContentInfo content,int flags,Bundle options) {
                        if(getIntent().getBooleanExtra("reject",false))return false;
                        try { content.requestPermission(); }
                        catch(Exception error) { report(error);return false; }
                        worker.execute(() -> receive(content,flags));
                        return true; // Real chat apps import asynchronously after accepting the callback.
                    }
                };
            }
        };
        editor.setId(android.R.id.edit);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setHint("Compose a local test message");
        editor.setText(getIntent().getStringExtra("initial"));
        editor.setSelection(editor.length());
        body.addView(editor,new LinearLayout.LayoutParams(-1,200));
        status=new TextView(this);status.setText("No attachment received");body.addView(status);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);body.addView(preview,new LinearLayout.LayoutParams(-1,420));
        setContentView(body);
        editor.requestFocus();editor.postDelayed(() -> getSystemService(InputMethodManager.class).showSoftInput(editor,0),200);
    }
    @Override public void onResume() {
        super.onResume();
        if(resumes++>0) {
            try {Thread.sleep(getIntent().getIntExtra("resumeDelay",0));}
            catch(InterruptedException error) {Thread.currentThread().interrupt();}
        }
        Drawable image=preview==null?null:preview.getDrawable();
        if(image instanceof AnimatedImageDrawable)((AnimatedImageDrawable)image).start();
    }
    @Override public void onStop() {
        Drawable image=preview==null?null:preview.getDrawable();
        if(image instanceof AnimatedImageDrawable)((AnimatedImageDrawable)image).stop();
        super.onStop();
    }
    private void receive(InputContentInfo content,int flags) {
        try {
            Thread.sleep(getIntent().getIntExtra("readDelay",400));
            String mime=getContentResolver().getType(content.getContentUri());
            long size;
            try(Cursor cursor=getContentResolver().query(content.getContentUri(),new String[]{OpenableColumns.SIZE},null,null,null)) {
                if(cursor==null||!cursor.moveToFirst())throw new IllegalStateException("Missing size");
                size=cursor.getLong(0);
            }
            boolean seekable=false,readOnly=false;
            if(!getIntent().getBooleanExtra("streamOnly",false)) {
                try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(content.getContentUri(),"r")) {
                    if(fd==null)throw new IllegalStateException("Missing file descriptor");
                    Os.lseek(fd.getFileDescriptor(),size-1,OsConstants.SEEK_SET);
                    Os.lseek(fd.getFileDescriptor(),0,OsConstants.SEEK_SET);
                    seekable=true;
                    readOnly=(Os.fcntlInt(fd.getFileDescriptor(),OsConstants.F_GETFL,0)&OsConstants.O_ACCMODE)==OsConstants.O_RDONLY;
                }
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long bytes=0;byte[] buffer=new byte[8192];
            try(InputStream input=getContentResolver().openInputStream(content.getContentUri())) {
                if(input==null)throw new IllegalStateException("Missing stream");
                for(int n;(n=input.read(buffer))!=-1;) {bytes+=n;digest.update(buffer,0,n);}
            }
            if(bytes!=size||bytes<=0)throw new IllegalStateException("Incorrect length");
            final Drawable image=ImageDecoder.decodeDrawable(ImageDecoder.createSource(getContentResolver(),content.getContentUri()));
            if(image.getIntrinsicWidth()<1||image.getIntrinsicHeight()<1)throw new IllegalStateException("Invalid image");
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));
            JSONObject result=new JSONObject().put("mime",mime).put("bytes",bytes).put("flags",flags)
                .put("seekable",seekable).put("readOnly",readOnly).put("animated",image instanceof AnimatedImageDrawable)
                .put("uri",content.getContentUri().toString()).put("hash",hash.toString());
            Log.i("ObadhExternalReceipt",result.toString());
            runOnUiThread(() -> {status.setText("Received "+mime+" ("+size+" bytes)");preview.setImageDrawable(image);if(image instanceof AnimatedImageDrawable)((AnimatedImageDrawable)image).start();});
        } catch(Exception error) {report(error);}
        finally {content.releasePermission();}
    }
    private void report(Exception error) {
        Log.e("ObadhExternalReceipt","Failed media import",error);
        runOnUiThread(() -> status.setText("Import failed: "+error));
    }
    @Override public void onDestroy() {worker.shutdownNow();preview.setImageDrawable(null);super.onDestroy();}
}
