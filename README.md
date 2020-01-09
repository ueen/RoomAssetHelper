# RoomAsset with selective Migration!

Thanks to MikeT https://stackoverflow.com/a/59637092/3123142

An Android helper class to manage database creation and version management using an application's raw asset files.

This library provides developers with a simple way to ship their Android app with an existing SQLite database (which may be pre-populated with data) and to manage its initial creation and any upgrades required with subsequent version releases.

It is implemented as an extension to `Room`, providing an easy way to use `Room` with an existing SQLite database.

---

# Gradle Dependency

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/b2a019a18e3a48e5b50ae4a5f1ed3135)](https://www.codacy.com/app/humazed/RoomAsset?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=humazed/RoomAsset&amp;utm_campaign=Badge_Grade)
[![Android Arsenal]( https://img.shields.io/badge/Android%20Arsenal-RoomAsset-green.svg?style=flat )]( https://android-arsenal.com/details/1/6421 )
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0.html)


### Dependency

Add this to your module's `build.gradle` file (make sure the version matches the last [release](https://github.com/ueen/RoomAsset/releases/latest)):

Add it in your root build.gradle at the end of repositories:

```gradle
allprojects {
	repositories {
		...
		maven { url "https://jitpack.io" }
	}
}
```

Add the dependency
```gradle
dependencies {
    // ... other dependencies
    implementation 'com.github.ueen:RoomAssetHelper:0.9'
}
```
# Basic Usage

`RoomAsset` is intended as a drop in alternative for the framework's [Room](https://developer.android.com/topic/libraries/architecture/room.html).

You can use `RoomAsset` as you use `Room` but with two changes:

1. Use `RoomAssetHelper.databaseBuilder()` instead of `Room.databaseBuilder()` 
2. Also specify the version as last parameter in the databaseBuilder
3. (optional) Specify a path under "assets" like `databases/"`
4. (optional) Specify the Table and columns you want to preserve

```kotlin
  val db = RoomAssetHelper.databaseBuilder(applicationContext, 
  					   AppDatabase::class.java,
					   "chinook.db",
					   1)
	   			.build()
```

`RoomAsset` relies upon asset file and folder naming conventions. Your `assets` folder will either be under your project root, or under `src/main` if you are using the default gradle project structure. At minimum, you must provide the following:

* A SQLite database inside the `assets` folder whose file name matches the database name you provide in code (including the file extension, if any)

For the example above, the project would contain the following:

    assets/chinook.db
   
If your database is in a subfolder of `assets` you need to add the path relative to the assets folder in the `databaseBuilder` this might look like this

```kotlin
  val db = RoomAssetHelper.databaseBuilder(applicationContext,
  					   AppDatabase::class.java,
					   "chinook.db",
					   databasePath = "databases/",
					   1)
				.build()
```

# Selective Migration

To preserve certain columns in your Database on the device (eg user data) you can add them in the `databaseBuilder` according to this schema

```
TablePreserve
	table: String 			//name of the table in which columns should be preserved
	preserveColumns: Array<String>, //name(s) of the columns which should be preserved on the device
    	macthByColumns: Array<String>	//unique identifier(s) (typically a `id` column) to match the rows
```
	
Important note: The original, as well as the new database must contain the columns you want to preserve and match by!

So in the end it might look something like this

```kotlin
  val db = RoomAssetHelper.databaseBuilder(applicationContext,
  					   AppDatabase::class.java, 
					   "chinook.db",
					   1,
					   preserve = arrayOf(TablePreserve(table = "yourTable",
					   				    preserveColumns = arrayOf("yourColumn"),
									    macthByColumns = arrayOf("id"))))
				.build()
```

# Upgrade Database

If you want to upgrade the database, overwrite the old Database in the assets and increase the version number of the Database AND in the databaseBuilder, like this

```kotlin
  val db = RoomAssetHelper.databaseBuilder(applicationContext,
  					   AppDatabase::class.java, 
					   "chinook.db", 
					   version = 2)
				.build()
```

The library will throw a `SQLiteAssetHelperException` if you do not provide the appropriately named file.

Supported data types: `TEXT`, `INTEGER`, `REAL`, `BLOB`


The [sample](https://github.com/humazed/RoomAsset/tree/master/sample) project demonstrates a simple database creation and usage example using the classic [Chinook database](http://www.sqlitetutorial.net/sqlite-sample-database).




License
-------

    Copyright (C) 2020

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 [1]: https://search.maven.org/remote_content?g=com.readystatesoftware.sqliteasset&a=sqliteassethelper&v=LATEST
