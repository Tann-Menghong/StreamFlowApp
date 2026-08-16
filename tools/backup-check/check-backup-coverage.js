// Fails if a Room entity has no corresponding section in the backup JSON.
//
// This bug class has now hit twice: bookmarks were missing from backups, and
// then custom website tabs were too. Both were silent — export succeeded, the
// file looked fine, and the data was simply gone after a restore onto a new
// phone. Nothing in the compiler or lint can see it, because a backup that
// omits a table is perfectly valid code.
//
// Anything deliberately excluded must be listed in INTENTIONALLY_EXCLUDED with
// a reason, so "not backed up" is always a decision someone wrote down rather
// than an oversight.
const fs = require('fs');

const DB = 'app/src/main/java/com/streamflow/data/local/AppDatabase.kt';
const BACKUP = 'app/src/main/java/com/streamflow/data/BackupManager.kt';

const INTENTIONALLY_EXCLUDED = {
  DownloadEntity:
    'downloaded media files live on the device; a JSON backup cannot carry them ' +
    'and restoring rows without files would list downloads that do not exist',
  PlaylistItemEntity:
    'exported nested inside each playlist rather than as a top-level section',
};

// Entity class name -> the JSON key expected in the backup.
const KEY_FOR = {
  FavoriteEntity: 'favorites',
  HistoryEntity: 'history',
  WatchLaterEntity: 'watchLater',
  SubscriptionEntity: 'subscriptions',
  BlockedItemEntity: 'blocked',
  PlaylistEntity: 'playlists',
  BookmarkEntity: 'bookmarks',
  CustomTabEntity: 'customTabs',
};

const dbSrc = fs.readFileSync(DB, 'utf8');
const backupSrc = fs.readFileSync(BACKUP, 'utf8');

const entitiesBlock = dbSrc.match(/entities\s*=\s*\[([\s\S]*?)\]/);
if (!entitiesBlock) {
  console.error('Could not find the @Database entities list');
  process.exit(2);
}
const entities = [...entitiesBlock[1].matchAll(/(\w+)::class/g)].map((m) => m[1]);

const backupKeys = new Set(
  [...backupSrc.matchAll(/put\("(\w+)"/g)].map((m) => m[1])
);

let missing = 0;
console.log('entities in @Database: ' + entities.length);
console.log('');
for (const e of entities) {
  if (INTENTIONALLY_EXCLUDED[e]) {
    console.log('SKIP  ' + e + '  — ' + INTENTIONALLY_EXCLUDED[e]);
    continue;
  }
  const key = KEY_FOR[e];
  if (!key) {
    missing++;
    console.log('FAIL  ' + e + '  — no backup key mapped. Add it to the backup, ' +
      'or to INTENTIONALLY_EXCLUDED with a reason.');
    continue;
  }
  if (backupKeys.has(key)) {
    console.log('OK    ' + e + '  -> "' + key + '"');
  } else {
    missing++;
    console.log('FAIL  ' + e + '  — expected key "' + key + '" is not written by BackupManager');
  }
}

console.log('');
if (missing === 0) {
  console.log('PASS - every entity is backed up or explicitly excluded');
} else {
  console.log('FAIL - ' + missing + ' entity(s) would be lost on restore');
}
process.exit(missing === 0 ? 0 : 1);
