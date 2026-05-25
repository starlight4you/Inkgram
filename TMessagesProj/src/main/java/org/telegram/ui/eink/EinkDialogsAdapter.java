package org.telegram.ui.eink;

import android.content.Context;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;
import java.util.ArrayList;

public class EinkDialogsAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();

    public EinkDialogsAdapter(Context context) {
        mContext = context;
    }

    public void setDialogs(ArrayList<TLRPC.Dialog> newDialogs) {
        if (newDialogs != null) {
            dialogs = new ArrayList<>(newDialogs);
        } else {
            dialogs.clear();
        }
        notifyDataSetChanged();
    }

    public TLRPC.Dialog getItem(int position) {
        if (position < 0 || position >= dialogs.size()) {
            return null;
        }
        return dialogs.get(position);
    }

    @Override
    public int getItemCount() {
        return dialogs.size();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        EinkDialogCell cell = new EinkDialogCell(mContext);
        cell.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        ));
        return new RecyclerListView.Holder(cell);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        EinkDialogCell cell = (EinkDialogCell) holder.itemView;
        TLRPC.Dialog dialog = dialogs.get(position);
        cell.setDialog(dialog);
    }
}
