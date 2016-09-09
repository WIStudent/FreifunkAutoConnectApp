package com.example.tobiastrumm.freifunkautoconnect;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

public class RemoveAllDialogFragment extends DialogFragment {


   public interface OnRemoveAllListener{
       /**
        * This method should remove all shown networks from the network configuration.
        */
       void removeAllNetworks();
   }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.dialog_remove_all)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    ((OnRemoveAllListener)getActivity()).removeAllNetworks();
                })
                .setNegativeButton(R.string.no, (dialog, id) -> {
                    // User cancelled the dialog
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
