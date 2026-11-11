package com.example.audiorecorderpro;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;

/** Share / rename / delete dialogs shared by the recording screens. */
final class RecordingActions {

    interface RenameCallback {
        void onRenamed(File newFile);
    }

    private RecordingActions() { }

    static void share(Activity a, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("audio/mp4");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(send, "Share recording"));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            Toast.makeText(a, "Could not share this recording.", Toast.LENGTH_LONG).show();
        }
    }

    static void rename(Activity a, File file, RenameCallback callback) {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setText(RecorderUtils.displayName(file));
        input.setSelection(input.getText().length());
        int pad = UiUtil.dp(a, 20);

        LinearLayout box = new LinearLayout(a);
        box.setPadding(pad, UiUtil.dp(a, 8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(a)
                .setTitle("Rename Recording")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String[] error = new String[1];
                    File renamed = RecorderUtils.renameRecording(a, file, input.getText().toString(), error);
                    if (renamed == null) {
                        Toast.makeText(a, error[0], Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(a, "Action completed successfully", Toast.LENGTH_SHORT).show();
                        callback.onRenamed(renamed);
                    }
                })
                .show();
    }

    static void confirmDelete(Activity a, File file, Runnable onDeleted) {
        new AlertDialog.Builder(a)
                .setTitle("Delete Recording?")
                .setMessage("This recording will be removed from your local library and storage permanently.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    String name = file.getName();
                    if (file.delete()) {
                        RecorderUtils.onDeleted(a, name);
                        Toast.makeText(a, "Recording deleted.", Toast.LENGTH_SHORT).show();
                        onDeleted.run();
                    } else {
                        Toast.makeText(a, "Could not delete the recording.", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    static File fromIntent(Activity a, String extra) {
        String path = a.getIntent().getStringExtra(extra);
        if (path == null) {
            return null;
        }
        File f = new File(path);
        return f.exists() ? f : null;
    }
}
