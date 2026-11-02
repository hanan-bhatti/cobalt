-- Historical v8 schema from commit 7486169; no note bodies in this fixture.
CREATE TABLE saved_items (`id` TEXT NOT NULL PRIMARY KEY, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `foldersJson` TEXT NOT NULL, `extractedText` TEXT, `thumbnailPath` TEXT, `orderIndex` REAL NOT NULL, `isSynced` INTEGER NOT NULL, `linkTitle` TEXT, `linkDescription` TEXT, `linkImage` TEXT, `isBackedUp` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `isPendingBackup` INTEGER NOT NULL, `isUnavailable` INTEGER NOT NULL, `mediaType` TEXT, `watchStatus` TEXT, `genresJson` TEXT NOT NULL, `watchProvidersJson` TEXT NOT NULL, `trailerUrl` TEXT, `backdropUrl` TEXT, `releaseYear` TEXT, `rating` REAL, `runtime` INTEGER, `tagline` TEXT, `budget` INTEGER, `revenue` INTEGER, `status` TEXT, `productionCompaniesJson` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `traktId` INTEGER, `tmdbId` INTEGER, `imdbId` TEXT, `userRating` INTEGER, `lastWatchedAt` INTEGER, `showProgressJson` TEXT);
CREATE TABLE custom_folders (`name` TEXT NOT NULL PRIMARY KEY, `colorHex` TEXT, `iconName` TEXT, `isPinned` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL);
CREATE INDEX index_saved_items_timestamp ON saved_items (`timestamp`);
CREATE INDEX index_saved_items_type ON saved_items (`type`);
CREATE INDEX index_saved_items_isArchived ON saved_items (`isArchived`);
CREATE INDEX index_saved_items_updatedAt ON saved_items (`updatedAt`);
CREATE INDEX index_saved_items_traktId ON saved_items (`traktId`);
CREATE INDEX index_saved_items_tmdbId ON saved_items (`tmdbId`);
