package com.portallium.notekeeper.ui.list;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.database.StorageKeeper;
import com.portallium.notekeeper.ui.auth.FirebaseLoginActivity;
import com.portallium.notekeeper.ui.note.create.NoteParametersPickerDialogFragment;
import com.portallium.notekeeper.ui.notepad.create.NotepadParametersPickerDialogFragment;
import com.portallium.notekeeper.utilities.ConnectionHelper;

import java.util.List;

abstract class AbstractListFragment<T> extends Fragment {

    private RecyclerView mRecyclerView;
    private AbstractListAdapter<T> mAdapter;

    public static final int REQUEST_PARAMETERS = 0;
    public static final String PARAMETERS_DIALOG = "NoteParametersDialog";

    public static final int NEW_NOTE_REQUEST = 0;
    public static final int NEW_NOTEPAD_REQUEST = 1;
    public static final int RENAME_NOTEPAD_REQUEST = 2;


    abstract List<T> getElementsListFromDatabase();
    abstract AbstractListAdapter<T> createAdapter(List<T> elementsList);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_notepads_list, menu);
        //todo: set subtitle: email
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);

        mRecyclerView = view.findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mRecyclerView.getContext(), DividerItemDecoration.VERTICAL));

        updateUI();

        return view;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.icon_new_note) {
            //fixme а если ни одного блокнота нет?! все же сломается. если я хочу запилить удаление блокнотов, мне надо что-то сделать и с этим тоже.
            return startDialogFragment(NoteParametersPickerDialogFragment.newInstance(getActivity().getIntent().getIntExtra(ListActivity.EXTRA_USER_ID, -1), getActivity().getIntent().getStringExtra(ListActivity.EXTRA_FIREBASE_ID)));
        } else if (item.getItemId() == R.id.icon_new_notepad) {
            return startDialogFragment(NotepadParametersPickerDialogFragment.newInstance(getActivity().getIntent().getIntExtra(ListActivity.EXTRA_USER_ID, -1), getActivity().getIntent().getStringExtra(ListActivity.EXTRA_FIREBASE_ID)));
        } else if (item.getItemId() == R.id.icon_log_out) {
            startActivity(FirebaseLoginActivity.getIntent(getActivity()));
            getActivity().finish();
            return true;
        } else if (item.getItemId() == R.id.icon_synchronize) {
            item.setEnabled(false);
            Toast.makeText(getActivity(), getString(R.string.synchronize_start), Toast.LENGTH_SHORT).show();
            SynchronizeTask synchronizeTask = new SynchronizeTask(item);
            synchronizeTask.execute();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void updateUI() {
        List<T> elements = getElementsListFromDatabase();
        if (mAdapter == null) {
            mAdapter = createAdapter(elements);
            mRecyclerView.setAdapter(mAdapter);
        } else {
            mAdapter.setElements(elements);
            mAdapter.notifyDataSetChanged();
        }
    }


    //NoteParametersPickerDialogFragment вызывает этот метод эксплицитно, когда закрывается.
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
        updateUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    //И никакой больше рефлексии. Не понимаю, почему я сразу так не сделал.
    protected boolean startDialogFragment(DialogFragment fragment) {
        fragment.setTargetFragment(this, REQUEST_PARAMETERS);
        fragment.show(getFragmentManager(), PARAMETERS_DIALOG);
        return true;
    }

    //я пока не знаю, какие модификаторы доступа тут нужны, поэтому so far все package-private.
    //todo: удалить этот класс. он получился довольно бесполезный, хотя метод bind тут к месту.
    abstract class AbstractViewHolder<E> extends RecyclerView.ViewHolder{

        public abstract void bind(E element);

        AbstractViewHolder (LayoutInflater inflater, ViewGroup parent, int layoutId) {
            super(inflater.inflate(layoutId, parent, false));
        }
    }

    //дженерик - это отображаемая структура, Note либо Notepad в нашем случае.
    abstract class AbstractListAdapter<E> extends RecyclerView.Adapter<AbstractViewHolder<E>> {

        List<E> elements;

        AbstractListAdapter(List<E> elements){
            setElements(elements);
        }

        void setElements(List<E> elements){
            this.elements = elements;//а это вообще законно?
        }

        @Override
        public int getItemCount() {
            return elements.size();
        }

        @Override
        public abstract AbstractViewHolder<E> onCreateViewHolder(ViewGroup parent, int viewType);

        @Override
        public void onBindViewHolder(AbstractViewHolder<E> holder, int position) {
            holder.bind(elements.get(position));
        }
    }

    private class SynchronizeTask extends AsyncTask<Void, Void, Boolean> {

        private MenuItem mSynchronizeIcon;

        SynchronizeTask(MenuItem icon) {
            this.mSynchronizeIcon = icon;
        }

        @Override
        protected Boolean doInBackground(Void... nothing) {
            //если интернета нет, то пусть синхронизатор даже не пытается
            if (!ConnectionHelper.isDeviceOnline(getActivity())) {
                //fixme: не работает.
                Log.e("Synchronization", "error: no internet connection");
                return false;
                //todo: тост о том, что интернета не найдено?
            }
            boolean additionSuccessful = StorageKeeper.getInstance(getActivity(), getActivity().getIntent().getStringExtra(ListActivity.EXTRA_FIREBASE_ID)).synchronizeNotepads(getActivity().getIntent().getIntExtra(ListActivity.EXTRA_USER_ID, 0));
            if (!additionSuccessful) {
                Log.e("Synchronization: SQLite", getString(R.string.synchronize_fail));
            } else {
                Log.i("Synchronization: SQLite", getString(R.string.synchronize_success));
            }
            //todo если все хорошо, синхронизировать заметки.
            return additionSuccessful;
            //todo: добавить синхронизацию сразу после авторизации (на каждом запуске).
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mSynchronizeIcon.setEnabled(true);
            if (result) {
                updateUI();
            }
        }
    }

}