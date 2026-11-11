package io.github.mirusu400.aram.app;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

/**
 * Reads a persistently authorized Android SAF folder recursively.
 * Only URI + display name metadata is collected: ZIP bytes are never copied.
 */
final class AndroidGameLibrary {
    private AndroidGameLibrary() {}

    private static final class Entry {
        final String name;
        final Uri uri;
        Entry(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    static String scan(ContentResolver resolver, Uri tree) throws IOException {
        final String root;
        try {
            root = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException error) {
            throw new IOException("Invalid game library folder", error);
        }
        ArrayDeque<String> directories = new ArrayDeque<>();
        HashSet<String> visited = new HashSet<>();
        ArrayList<Entry> games = new ArrayList<>();
        directories.add(root);
        while (!directories.isEmpty()) {
            String id = directories.removeLast();
            if (!visited.add(id)) continue;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
            try (Cursor cursor = resolver.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, null)) {
                if (cursor == null) {
                    if (id.equals(root)) throw new IOException("Unable to read selected folder");
                    continue;
                }
                while (cursor.moveToNext()) {
                    String childId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (childId == null || name == null) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        if (!visited.contains(childId)) directories.add(childId);
                    } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")
                            && visited.add(childId)) {
                        games.add(new Entry(name,
                                DocumentsContract.buildDocumentUriUsingTree(tree, childId)));
                    }
                }
            } catch (RuntimeException error) {
                if (id.equals(root)) throw new IOException("Cannot access selected folder", error);
                // A provider may deny one subfolder; retain the other results.
            }
        }
        Collections.sort(games, (a, b) ->
                a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT)));
        JSONArray result = new JSONArray();
        for (Entry game : games) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", game.name);
                item.put("uri", game.uri.toString());
            } catch (JSONException error) {
                throw new IOException("Cannot encode game library", error);
            }
            result.put(item);
        }
        return result.toString();
    }

    static String folderLabel(Uri tree) {
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            int separator = Math.max(id.lastIndexOf('/'), id.lastIndexOf(':'));
            String label = separator >= 0 ? id.substring(separator + 1) : id;
            return label.isEmpty() ? "Android game folder" : label;
        } catch (RuntimeException ignored) {
            return "Android game folder";
        }
    }
}
