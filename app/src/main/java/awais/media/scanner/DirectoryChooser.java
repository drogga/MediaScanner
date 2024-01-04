package awais.media.scanner;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import awais.media.scanner.utils.Utils;

/** @noinspection deprecation */
public final class DirectoryChooser extends DialogFragment {
    public static final String KEY_CURRENT_DIRECTORY = "CURRENT_DIRECTORY";
    private Activity activity;
    private String initialDirectory;
    private OnFragmentInteractionListener interactionListener;
    private View btnConfirm;
    private TextView tvSelectedFolder;
    private ImageButton btnNavUp;
    private ArrayAdapter<String> listDirectoriesAdapter;
    private List<String> fileNames;
    private FileObserver fileObserver;
    private File selectedDir;
    private File[] filesInDir;

    public static void showChooser(final Main main) {
        new DirectoryChooser().setInteractionListener(main).show(main.getFragmentManager(), "AIZA");
    }

    public DirectoryChooser() {
        super();
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;

        if (activity instanceof OnFragmentInteractionListener)
            interactionListener = (OnFragmentInteractionListener) activity;
        else {
            final Fragment owner = getTargetFragment();
            if (owner instanceof OnFragmentInteractionListener)
                interactionListener = (OnFragmentInteractionListener) owner;
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        Context context = activity;
        if (context == null) context = getActivity();
        if (context == null) context = container.getContext();
        if (context == null) context = inflater.getContext();
        if (context == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context = getContext();

        final View view = inflater.inflate(R.layout.directory_chooser, container, false);

        final Button mBtnCancel = view.findViewById(R.id.btnCancel);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnNavUp = view.findViewById(R.id.btnNavUp);
        tvSelectedFolder = view.findViewById(R.id.txtvSelectedFolder);

        int color = 0xFFFFFF;
        final Resources.Theme theme = activity.getTheme();
        if (theme != null) {
            final TypedArray backgroundAttributes = theme.obtainStyledAttributes(new int[]{android.R.attr.colorBackground});
            color = backgroundAttributes.getColor(0, 0xFFFFFF);
            backgroundAttributes.recycle();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    backgroundAttributes.close();
                } catch (final Exception e) {
                    // ignore
                }
            }
        }
        if (color != 0xFFFFFF && 0.21 * Color.red(color) + 0.72 * Color.green(color) + 0.07 * Color.blue(color) < 128) {
            btnNavUp.setImageResource(R.drawable.nav_up_light);
        }

        final View.OnClickListener clickListener = v -> {
            if (v == btnConfirm) {
                if (isValidFile(selectedDir)) returnSelectedFolder();
                dismiss();
            } else if (v == btnNavUp) {
                final File parent;
                if (selectedDir != null && (parent = selectedDir.getParentFile()) != null) changeDirectory(parent);
            } else if (v == mBtnCancel) {
                dismiss();
            }
        };

        mBtnCancel.setOnClickListener(clickListener);
        btnConfirm.setOnClickListener(clickListener);
        btnNavUp.setOnClickListener(clickListener);

        fileNames = new ArrayList<>();
        listDirectoriesAdapter = new ArrayAdapter<>(Objects.requireNonNull(context), android.R.layout.simple_list_item_1, fileNames);

        final ListView mListDirectories = view.findViewById(R.id.directoryList);
        mListDirectories.setOnItemClickListener((parent, view1, position, id) -> {
            if (filesInDir != null && position >= 0
                && position < filesInDir.length) {
                changeDirectory(filesInDir[position]);
            }
        });
        mListDirectories.setAdapter(listDirectoriesAdapter);

        final File initialDir;
        if (!TextUtils.isEmpty(initialDirectory) && isValidFile(new File(initialDirectory))) {
            initialDir = new File(initialDirectory);
        } else {
            initialDir = Environment.getExternalStorageDirectory();
        }

        changeDirectory(initialDir);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fileObserver != null) fileObserver.startWatching();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (fileObserver != null) fileObserver.stopWatching();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        interactionListener = null;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialDirectory = Utils.sdcardPath;
        if (savedInstanceState != null) {
            initialDirectory = savedInstanceState.getString(KEY_CURRENT_DIRECTORY);
            if (initialDirectory == null) initialDirectory = Utils.sdcardPath;
        }
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        return new Dialog(getActivity(), R.style.AppTheme_Dialog) {
            @Override
            public void onBackPressed() {
                if (selectedDir != null) {
                    final String absolutePath = selectedDir.getAbsolutePath();
                    if (absolutePath.equals(initialDirectory) || absolutePath.equals(new File(initialDirectory).getAbsolutePath()))
                        dismiss();
                    else {
                        final File parentFile = selectedDir.getParentFile();
                        if (parentFile != null) changeDirectory(parentFile);
                    }
                }
            }
        };
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedDir != null) outState.putString(KEY_CURRENT_DIRECTORY, selectedDir.getAbsolutePath());
    }

    private void changeDirectory(final File dir) {
        if (dir != null && dir.isDirectory()) {
            final String path = dir.getAbsolutePath();

            final File[] contents = dir.listFiles();
            if (contents != null) {
                int numDirectories = 0;
                for (final File f : contents) {
                    if (f.isDirectory()) {
                        numDirectories++;
                    }
                }
                filesInDir = new File[numDirectories];
                fileNames.clear();
                for (int i = 0, counter = 0; i < numDirectories; counter++) {
                    if (contents[counter].isDirectory()) {
                        filesInDir[i] = contents[counter];
                        fileNames.add(contents[counter].getName());
                        i++;
                    }
                }
                Arrays.sort(filesInDir);
                Collections.sort(fileNames);
                selectedDir = dir;
                tvSelectedFolder.setText(path);
                listDirectoriesAdapter.notifyDataSetChanged();
                fileObserver = new FileObserver(path, FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_FROM | FileObserver.MOVED_TO) {
                    @Override
                    public void onEvent(final int event, final String path) {
                        if (activity != null) activity.runOnUiThread(() -> refreshDirectory());
                    }
                };
                fileObserver.startWatching();
            }
        }
        refreshButtonState();
    }

    private void refreshButtonState() {
        if (selectedDir != null) {
            final String path = selectedDir.getAbsolutePath();
            toggleUpButton(!(path.equals(initialDirectory) || path.equals(Utils.sdcardPathFile.getAbsolutePath()) || selectedDir == Utils.sdcardPathFile));
            btnConfirm.setEnabled(isValidFile(selectedDir));
        }
    }

    private void refreshDirectory() {
        if (selectedDir != null) changeDirectory(selectedDir);
    }

    public DirectoryChooser setInteractionListener(final OnFragmentInteractionListener interactionListener) {
        this.interactionListener = interactionListener;
        return this;
    }

    private void toggleUpButton(final boolean enable) {
        if (btnNavUp != null) {
            btnNavUp.setEnabled(enable);
            btnNavUp.setAlpha(enable ? 1f : 0.617f);
        }
    }

    private void returnSelectedFolder() {
        if (interactionListener != null && selectedDir != null)
            interactionListener.onSelectDirectory(selectedDir.getAbsolutePath());
    }

    private boolean isValidFile(final File file) {
        return file != null && file.isDirectory() && file.canRead();
    }

    public interface OnFragmentInteractionListener {
        void onSelectDirectory(final String path);
    }
}
