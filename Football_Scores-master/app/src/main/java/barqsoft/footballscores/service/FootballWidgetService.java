package barqsoft.footballscores.service;

/**
 * Created by Raúl Feliz Alonso on 23/07/15.
 */
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import barqsoft.footballscores.DatabaseContract;
import barqsoft.footballscores.R;
import barqsoft.footballscores.Utilies;
import barqsoft.footballscores.WidgetItem;
import barqsoft.footballscores.scoresAdapter;

public class FootballWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new FootballRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class FootballRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private List<WidgetItem> mWidgetItems = new ArrayList<>();
    private Context mContext;
    private int mAppWidgetId;

    public FootballRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    public void onCreate() {

        //get data from content provider
        ContentResolver cr = mContext.getContentResolver();
        String[] fragmentDate = new String[1];

        Date dFragmentDate = new Date(System.currentTimeMillis());
        SimpleDateFormat mformat = new SimpleDateFormat("yyyy-MM-dd");
        fragmentDate[0] = mformat.format(dFragmentDate);
        //get data from database
        Cursor cursor = cr.query(DatabaseContract.scores_table.buildScoreWithDate(),
                null, //Columns
                null,       //Conditions
                fragmentDate,       //Arguments
                null);      //Order

        if (cursor.moveToFirst())
        {
            do
            {
                WidgetItem item = new WidgetItem();
                item.home_name = cursor.getString(scoresAdapter.COL_HOME);
                item.away_name = cursor.getString(scoresAdapter.COL_AWAY);
                item.score = Utilies.getScores(cursor.getInt(scoresAdapter.COL_HOME_GOALS), cursor.getInt(scoresAdapter.COL_AWAY_GOALS));
                mWidgetItems.add(item);

            } while (cursor.moveToNext());
        }

    }

    public void onDestroy() {
        // In onDestroy() you should tear down anything that was setup for your data source,
        // eg. cursors, connections, etc.
        mWidgetItems.clear();
    }

    public int getCount() {
        return mWidgetItems.size();
    }

    public RemoteViews getViewAt(int position) {
        // position will always range from 0 to getCount() - 1.

        // We construct a remote views item based on our widget item xml file, and set the
        // text based on the position.
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.scores_widget_item);
        rv.setTextViewText(R.id.home_name_widget, mWidgetItems.get(position).home_name);
        rv.setTextViewText(R.id.away_name_widget, mWidgetItems.get(position).away_name);
        rv.setTextViewText(R.id.score_textview_widget, mWidgetItems.get(position).score);

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in StackWidgetProvider.
        Intent fillInIntent = new Intent();
        rv.setOnClickFillInIntent(R.id.scores_widget_item, fillInIntent);



        // Return the remote views object.
        return rv;
    }

    public RemoteViews getLoadingView() {
        // You can create a custom loading view (for instance when getViewAt() is slow.) If you
        // return null here, you will get the default loading view.
        return null;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public long getItemId(int position) {
        return position;
    }

    public boolean hasStableIds() {
        return true;
    }

    public void onDataSetChanged() {
        // This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
        // on the collection view corresponding to this factory. You can do heaving lifting in
        // here, synchronously. For example, if you need to process an image, fetch something
        // from the network, etc., it is ok to do it here, synchronously. The widget will remain
        // in its current state while work is being done here, so you don't need to worry about
        // locking up the widget.
    }
}