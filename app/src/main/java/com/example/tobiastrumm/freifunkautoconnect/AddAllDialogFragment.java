package com.example.tobiastrumm.freifunkautoconnect;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

public class AddAllDialogFragment extends DialogFragment {

    public interface OnAddAllListener{
        /**
         * This method should add all shown networks to the network configuration.
         */
        void addAllNetworks();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.dialog_add_all)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    ((OnAddAllListener)getActivity()).addAllNetworks();
                })
                .setNegativeButton(R.string.no, (dialog, id) -> {
                    // User cancelled the dialog
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
