package com.besome.sketch.editor.property;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import a.a.a.Kw;
import a.a.a.jC;
import a.a.a.mB;
import a.a.a.wB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;

public class PropertyFontItem extends RelativeLayout implements View.OnClickListener {

    private String sc_id;
    private String key;
    private String value;
    private TextView tvName;
    private TextView tvValue;
    private ImageView imgIcon;
    private Kw onPropertyValueChangeListener;
    private FontAdapter adapter;

    public PropertyFontItem(Context context) {
        super(context);
        initialize(context);
    }

    public void a(String sc_id) {
        this.sc_id = sc_id;
    }

    private void initialize(Context context) {
        wB.a(context, this, R.layout.property_resource_item);
        tvName = findViewById(R.id.tv_name);
        tvValue = findViewById(R.id.tv_value);
        imgIcon = findViewById(R.id.img_left_icon);
        findViewById(R.id.property_item).setOnClickListener(this);
        findViewById(R.id.property_menu_item).setVisibility(View.GONE);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        int identifier = getResources().getIdentifier(key, "string", getContext().getPackageName());
        if (identifier > 0) {
            tvName.setText(Helper.getResString(identifier));
            imgIcon.setImageResource(R.drawable.ic_mtrl_formattext);
        }
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        tvValue.setText(value);
    }

    @Override
    public void onClick(View v) {
        if (mB.a()) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(Helper.getText(tvName));
        builder.setIcon(R.drawable.ic_mtrl_formattext);

        View view = wB.a(getContext(), R.layout.property_popup_selector_font);
        RecyclerView recyclerView = view.findViewById(R.id.font_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        ArrayList<ProjectResourceBean> fonts = jC.d(sc_id).d;
        ArrayList<String> fontNames = new ArrayList<>();
        fontNames.add("none");
        for (ProjectResourceBean font : fonts) {
            fontNames.add(font.resName);
        }

        adapter = new FontAdapter(fontNames, value);
        recyclerView.setAdapter(adapter);

        builder.setView(view);
        builder.setPositiveButton(R.string.common_word_select, (dialog, which) -> {
            String selectedFont = adapter.getSelectedFont();
            setValue(selectedFont);
            if (onPropertyValueChangeListener != null) {
                onPropertyValueChangeListener.a(key, value);
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(R.string.common_word_cancel, null);
        builder.show();
    }

    public void setOnPropertyValueChangeListener(Kw onPropertyValueChangeListener) {
        this.onPropertyValueChangeListener = onPropertyValueChangeListener;
    }

    private static class FontAdapter extends RecyclerView.Adapter<FontAdapter.ViewHolder> {

        private final List<String> fonts;
        private String selectedFont;
        private int selectedPosition = -1;

        public FontAdapter(List<String> fonts, String selectedFont) {
            this.fonts = fonts;
            this.selectedFont = selectedFont;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_single_choice, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String fontName = fonts.get(position);
            holder.textView.setText(fontName);
            if (fontName.equals(selectedFont)) {
                holder.textView.setChecked(true);
                selectedPosition = position;
            } else {
                holder.textView.setChecked(false);
            }

            holder.itemView.setOnClickListener(v -> {
                if (selectedPosition != holder.getAdapterPosition()) {
                    notifyItemChanged(selectedPosition);
                    selectedPosition = holder.getAdapterPosition();
                    notifyItemChanged(selectedPosition);
                    selectedFont = fonts.get(selectedPosition);
                }
            });
        }

        @Override
        public int getItemCount() {
            return fonts.size();
        }

        public String getSelectedFont() {
            return selectedFont;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public android.widget.CheckedTextView textView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = (android.widget.CheckedTextView) itemView;
            }
        }
    }
}
