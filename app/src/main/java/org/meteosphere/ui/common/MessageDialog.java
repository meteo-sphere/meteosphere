package org.meteosphere.ui.common;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** A message with an OK that closes it, which comes back when the screen is recreated */
public class MessageDialog extends DialogFragment {

    private static final String ARG_TEXT = "text";

    /** The message is kept in the arguments, which the screen's recreation gives back */
    public static MessageDialog newInstance(String message) {
        MessageDialog fragment = new MessageDialog();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_TEXT, message);
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // The system's own OK, in the language of the screen
        return new MaterialAlertDialogBuilder(requireContext())
                .setMessage(requireArguments().getString(ARG_TEXT))
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
