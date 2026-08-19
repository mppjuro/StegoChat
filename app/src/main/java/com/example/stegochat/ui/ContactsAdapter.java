package com.example.stegochat.ui;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.stegochat.R;
import com.example.stegochat.db.Contact;
import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {
    private List<Contact> contacts = new ArrayList<>();
    private OnContactClickListener listener;

    public interface OnContactLongClickListener {
        void onContactLongClick(Contact contact);
    }
    private OnContactLongClickListener longClickListener;

    public void setOnContactLongClickListener(OnContactLongClickListener listener) {
        this.longClickListener = listener;
    }
    public interface OnContactClickListener {
        void onContactClick(Contact contact);
    }

    public void setOnContactClickListener(OnContactClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = contacts.get(position);
        holder.nameText.setText(contact.name);
        holder.keyText.setText("ID: " + contact.conversationId);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onContactClick(contact);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onContactLongClick(contact);
                return true;
            }
            return false;
        });
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView keyText;

        ContactViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.contactNameText);
            keyText = itemView.findViewById(R.id.contactKeyText);
        }
    }
}