package info.geopost.geopost;

import com.parse.ParseClassName;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

@ParseClassName("GeoPostObj")
public class GeoPostObj extends ParseObject {

    public GeoPostObj() {}

    public String getText() {
        return getString("text");
    }

    public void setText(String value) {
        put("text", value);
    }

    public String getTitle(){
        return getString("title");
    }

    public void setTitle(String value){
        put("title", value);
    }

    public ParseUser getUser() {
        return getParseUser("user");
    }

    public void setUser(ParseUser value) {
        put("user", value);
    }

    public String getUsername() {return getString("username");}

    public void setUsername(String value) {put ("username", value);}

    public ParseGeoPoint getLocation() {
        return getParseGeoPoint("location");
    }

    public void setLocation(ParseGeoPoint value) {
        put("location", value);
    }

    public static ParseQuery<GeoPostObj> getQuery() {
        return ParseQuery.getQuery(GeoPostObj.class);
    }
}