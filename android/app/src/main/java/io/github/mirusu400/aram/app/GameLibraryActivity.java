package io.github.mirusu400.aram.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

/** Optional Android launcher for a persistent folder-based ZIP game library. */
public final class GameLibraryActivity extends Activity {
    private static final int PICK_FOLDER = 2011;
    private static final String PREFS = "game_library";
    private static final String TREE = "tree_uri";
    private final ArrayList<Game> games = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView status;

    private static final class Game {
        final String title;
        final Uri uri;
        Game(String title, Uri uri) { this.title = title; this.uri = uri; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        Button folder = new Button(this);
        folder.setText("게임 폴더 선택");
        folder.setOnClickListener(v -> selectFolder());
        layout.addView(folder);
        Button refresh = new Button(this);
        refresh.setText("목록 새로고침");
        refresh.setOnClickListener(v -> scan());
        layout.addView(refresh);
        status = new TextView(this);
        layout.addView(status);
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openGame(games.get(position)));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        scan();
    }

    private void selectFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FOLDER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TREE, uri.toString()).apply();
            scan();
        } catch (SecurityException e) {
            Toast.makeText(this, "폴더 읽기 권한을 저장할 수 없습니다", Toast.LENGTH_LONG).show();
        }
    }

    private void scan() {
        games.clear();
        adapter.clear();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(TREE, null);
        if (saved == null) { status.setText("게임 ZIP 폴더를 선택하세요."); return; }
        try {
            Uri tree = Uri.parse(saved);
            String root = DocumentsContract.getTreeDocumentId(tree);
            scanDirectory(tree, root, 0, new int[]{0});
            Collections.sort(games, (a, b) -> a.title.compareToIgnoreCase(b.title));
            for (Game game : games) adapter.add(game.title);
            status.setText("게임 " + games.size() + "개");
        } catch (Exception e) {
            status.setText("폴더를 읽을 수 없습니다. 다시 선택하세요.");
        }
    }

    private void scanDirectory(Uri tree, String id, int depth, int[] visited) {
        if (depth > 16 || visited[0] > 10000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        try (android.database.Cursor cursor = getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext() && visited[0]++ < 10000) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (name == null || childId == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanDirectory(tree, childId, depth + 1, visited);
                } else if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    games.add(new Game(name, DocumentsContract.buildDocumentUriUsingTree(tree, childId)));
                }
            }
        } catch (Exception ignored) {
            // Individual inaccessible subdirectories should not hide other games.
        }
    }

    private void openGame(Game game) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(game.uri, "application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
}
