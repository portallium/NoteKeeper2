package com.portallium.notekeeper.ui.list;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.portallium.notekeeper.R;
import com.portallium.notekeeper.ui.auth.FirebaseLoginActivity;
import com.portallium.notekeeper.ui.note.create.NoteParametersPickerDialogFragment;
import com.portallium.notekeeper.ui.notepad.create.NotepadParametersPickerDialogFragment;

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
            return startDialogFragment(NoteParametersPickerDialogFragment.newInstance(getActivity().getIntent().getIntExtra(ListActivity.EXTRA_USER_ID, -1), getActivity().getIntent().getStringExtra(ListActivity.EXTRA_FIREBASE_ID)));
        } else if (item.getItemId() == R.id.icon_new_notepad) {
            return startDialogFragment(NotepadParametersPickerDialogFragment.newInstance(getActivity().getIntent().getIntExtra(ListActivity.EXTRA_USER_ID, -1), getActivity().getIntent().getStringExtra(ListActivity.EXTRA_FIREBASE_ID)));
        } else if (item.getItemId() == R.id.icon_log_out) {
            startActivity(FirebaseLoginActivity.getIntent(getActivity()));
            getActivity().finish();
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
    abstract class AbstractViewHolder<E> extends RecyclerView.ViewHolder{

        abstract void bind(E element);

        //я пока не знаю, как мне этот layoutId передать.
        AbstractViewHolder (LayoutInflater inflater, ViewGroup parent, int layoutId) {
            super(inflater.inflate(layoutId, parent, false));
            //возможно, тут будет еще что-то.
        }
    }

    //дженерик - это отображаемая структура, Note либо Notepad в нашем случае.
    abstract class AbstractListAdapter<E> extends RecyclerView.Adapter<AbstractViewHolder<E>> {

        List<E> elements;
        //todo: с недавних пор у нас есть аж целых два источника заметок и блокнотов, firebase и локальная БД. выводиться должна, по идее, конъюнкция двух этих множеств. Как насчет, скажем, метода синхронизации где-нибудь?..

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
}