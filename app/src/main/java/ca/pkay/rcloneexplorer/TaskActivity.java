package ca.pkay.rcloneexplorer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.Task;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject;
import es.dmoral.toasty.Toasty;

public class TaskActivity extends AppCompatActivity {


    public static final String ID_EXTRA = "TASK_EDIT_ID";
    private final int REQUEST_CODE_FP_LOCAL = 500;
    private final int REQUEST_CODE_FP_REMOTE = 444;
    private Rclone rcloneInstance;
    private Task existingTask;
    private DatabaseHandler dbHandler;
    private String[] items;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_FP_LOCAL:
                EditText tv_local = findViewById(R.id.task_local_path_textfield);
                tv_local.setText(data.getStringExtra(FilePicker.FILE_PICKER_RESULT));
                break;
            case REQUEST_CODE_FP_REMOTE:
                Uri uri = null;
                if (data != null) {
                   String path = data.getData().toString();
                   try {
                       path = URLDecoder.decode(path, "UTF-8");
                   } catch (UnsupportedEncodingException e) {}
                   String provider = "content://io.github.x0b.rcx.vcp/tree/rclone/remotes/";
                   if(path.startsWith(provider)){
                       String[] parts = path.substring(provider.length()).split(":");
                       ((TextView)findViewById(R.id.task_remote_path_textfield)).setText(parts[1]);
                       int i=0;
                       for(String remote: items) {
                           if(remote.equals(parts[0])){
                               ((Spinner)findViewById(R.id.task_remote_spinner)).setSelection(i);
                           }
                           i++;
                       }
                   }else{
                       Toasty.error(this, "This Remote is not a RCX-Remote.").show();
                   }
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityHelper.applyTheme(this);
        setContentView(R.layout.activity_task);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        dbHandler = new DatabaseHandler(this);

        Bundle extras = getIntent().getExtras();
        Long task_id;

        if (extras != null) {
            task_id = extras.getLong(ID_EXTRA);
            if(task_id!=0){
                existingTask = dbHandler.getTask(task_id);
                if(existingTask == null){
                    Toasty.error(this, this.getResources().getString(R.string.taskactivity_task_not_found)).show();
                    finish();
                }
            }
        }

        final Context c = this.getApplicationContext();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            //Todo fix error when no remotes are available
            if(existingTask==null){
                saveTask();
            }else{
                persistTaskChanges();
            }
        });

        EditText tv_local = findViewById(R.id.task_local_path_textfield);
        tv_local.setOnFocusChangeListener((v, hasFocus) -> {
            if(hasFocus){
                Intent intent = new Intent(c, FilePicker.class);
                intent.putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true);
                startActivityForResult(intent, REQUEST_CODE_FP_LOCAL);
            }
        });

        EditText tv_remote = findViewById(R.id.task_remote_path_textfield);
        tv_remote.setOnFocusChangeListener((v, hasFocus) -> {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if(hasFocus && sharedPreferences.getBoolean(getString(R.string.pref_key_enable_vcp), false)){
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivityForResult(intent, REQUEST_CODE_FP_REMOTE);
            }
        });


        rcloneInstance = new Rclone(this);

        Spinner remoteDropdown = findViewById(R.id.task_remote_spinner);

        items = new String[rcloneInstance.getRemotes().size()];

        for (int i = 0; i< rcloneInstance.getRemotes().size(); i++) {
            items[i]= rcloneInstance.getRemotes().get(i).getName();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items);
        remoteDropdown.setAdapter(adapter);


        Spinner directionDropdown = findViewById(R.id.task_direction_spinner);
        String[] options = SyncDirectionObject.getOptionsArray(this);
        ArrayAdapter<String> directionAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
        directionDropdown.setAdapter(directionAdapter);
        populateFields(items);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void populateFields(String[] remotes) {
        if(existingTask!=null){
            ((TextView)findViewById(R.id.task_title_textfield)).setText(existingTask.getTitle());
            Spinner s = findViewById(R.id.task_remote_spinner);

            int i=0;
            for(String remote: remotes) {
                if(remote.equals(existingTask.getRemoteId())){
                    s.setSelection(i);
                }
                i++;
            }

            ((TextView)findViewById(R.id.task_remote_path_textfield)).setText(existingTask.getRemotePath());
            ((TextView)findViewById(R.id.task_local_path_textfield)).setText(existingTask.getLocalPath());
            ((Spinner)findViewById(R.id.task_direction_spinner)).setSelection(existingTask.getDirection()-1);
        }
    }

    private void persistTaskChanges(){
        dbHandler.updateTask(getTaskValues(existingTask.getId()));
        finish();
    }

    private void saveTask(){
        Task newTask = dbHandler.createTask(getTaskValues(0L));
        finish();
    }

    private Task getTaskValues(Long id ){
        Task taskToPopulate = new Task(id);
        taskToPopulate.setTitle(((EditText)findViewById(R.id.task_title_textfield)).getText().toString());

        String remotename=((Spinner)findViewById(R.id.task_remote_spinner)).getSelectedItem().toString();
        taskToPopulate.setRemoteId(remotename);

        int direction = ((Spinner)findViewById(R.id.task_direction_spinner)).getSelectedItemPosition()+1;
        for (RemoteItem ri: rcloneInstance.getRemotes()) {
            if(ri.getName().equals(taskToPopulate.getRemoteId())){
                taskToPopulate.setRemoteType(ri.getType());
            }
        }

        taskToPopulate.setRemotePath(((EditText)findViewById(R.id.task_remote_path_textfield)).getText().toString());
        taskToPopulate.setLocalPath(((EditText)findViewById(R.id.task_local_path_textfield)).getText().toString());
        taskToPopulate.setDirection(direction);
        return taskToPopulate;
    }

}