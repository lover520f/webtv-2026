package com.fongmi.android.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.EpgReminderRecord;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.EpgReminderDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class, EpgReminderRecord.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 38;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) {
            try {
                instance = create(App.get());
                // Room opens the database lazily on first DAO access; force the open here so a
                // missing or failed migration is caught by this catch (preserve + rebuild) instead
                // of crashing later in unrelated code paths on first DAO touch.
                instance.getOpenHelper().getWritableDatabase().close();
            } catch (Throwable e) {
                preserveFailedDatabase(App.get(), e);
                App.get().deleteDatabase(NAME);
                instance = create(App.get());
            }
        }
        return instance;
    }

    private static void preserveFailedDatabase(Context context, Throwable error) {
        SpiderDebug.log(error);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        preserveDatabaseFile(context.getDatabasePath(NAME), stamp);
        preserveDatabaseFile(context.getDatabasePath(NAME + "-wal"), stamp);
        preserveDatabaseFile(context.getDatabasePath(NAME + "-shm"), stamp);
    }

    private static void preserveDatabaseFile(File source, String stamp) {
        if (source == null || !source.exists()) return;
        File target = Path.cache(source.getName() + ".failed-" + stamp);
        byte[] buffer = new byte[16384];
        try (FileInputStream is = new FileInputStream(source); FileOutputStream os = new FileOutputStream(target)) {
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        } catch (Exception e) {
            SpiderDebug.log(e);
            Path.clear(target);
        }
    }

    public static void backup() {
        backup(new com.fongmi.android.tv.impl.Callback());
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback) {
        Task.execute(() -> {
            File file = new File(Path.tv(), "tv-" + LocalDate.now().format(Formatters.DATE) + ".bk");
            Backup backup = Backup.create();
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                Path.write(file, backup.toString().getBytes());
                FileUtil.gzipCompress(file);
                App.post(callback::success);
                cleanOld();
            }
        });
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback) {
        Task.execute(() -> {
            File restore = Path.cache("restore");
            try {
                FileUtil.gzipDecompress(file, restore);
                Backup backup = Backup.objectFrom(Path.read(restore));
                if (backup.getConfig().isEmpty()) {
                    App.post(callback::error);
                } else {
                    backup.restore();
                    Path.clear(restore);
                    App.post(callback::success);
                }
            } catch (Throwable e) {
                // A truncated or non-gzip file must surface as callback.error, not kill the
                // process from inside the executor thread.
                SpiderDebug.log("db-restore", e);
                Path.clear(restore);
                App.post(callback::error);
            }
        });
    }

    private static void cleanOld() {
        List<File> items = new ArrayList<>();
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) items.add(file);
        if (!items.isEmpty()) items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        if (items.size() > 7) for (int i = 7; i < items.size(); i++) Path.clear(items.get(i));
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .addMigrations(Migrations.MIGRATION_35_36)
                .addMigrations(Migrations.MIGRATION_36_37)
                .addMigrations(Migrations.MIGRATION_37_38)
                .setQueryExecutor(Task.executor())
                .setTransactionExecutor(Task.largeExecutor())
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();

    public abstract EpgReminderDao getEpgReminderDao();
}
