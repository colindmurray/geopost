package info.geopost.geopost;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import it.gmariotti.cardslib.library.internal.Card;
import it.gmariotti.cardslib.library.internal.CardArrayAdapter;
import it.gmariotti.cardslib.library.view.CardListView;


public class CommentActivity extends ActionBarActivity {

    public static GeoPostObj geoPostObj;
    private TextView username;
    private TextView body;
    private final String TAG = "CommentActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        ArrayList<Card> cards = new ArrayList<>();
        CommentCardHeader card = new CommentCardHeader(this, geoPostObj);
        cards.add(card);
        Date currentDate = geoPostObj.getCreatedAt();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.US);
        String time = format.format(currentDate);
        Log.e(TAG, "Simpledate Format: " + time);
        Log.e(TAG, "Hours is: " + currentDate.getHours());

        for (int i = 0; i < 15; i++) {
            CommentCardReply card_reply = new CommentCardReply(this, geoPostObj);
            cards.add(card_reply);
        }
        CardArrayAdapter mCardArrayAdapter = new CardArrayAdapter(this,cards);
        CardListView listView = (CardListView) findViewById(R.id.commentList);
        if (listView!=null){
            listView.setAdapter(mCardArrayAdapter);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_comment, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}