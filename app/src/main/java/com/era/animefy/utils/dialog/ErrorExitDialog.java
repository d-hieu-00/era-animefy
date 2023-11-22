package com.era.animefy.utils.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.era.animefy.R;

public class ErrorExitDialog extends AppCompatDialogFragment {
    private final String title;
    private final String message;

    public ErrorExitDialog(String inTitle, String inMsg) {
        super();
        title = inTitle;
        message = inMsg;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.txt_ok_btn, (dialogInterface, i) -> requireActivity().finish());
        return builder.create();
    }
}
