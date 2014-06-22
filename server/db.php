<?php

/*
    LifeStream - Instant Photo Sharing
    Copyright (C) 2014 Kayateia

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

require("config.php");

$db = new mysqli($db_host, $db_user, $db_pass, $db_database);

// check connection
if (mysqli_connect_errno()) {
	printf("Connect failed: %s\n", mysqli_connect_error());
	exit();
}

// Binds a statement's output to an associative array.
// http://us.php.net/manual/en/mysqli-stmt.fetch.php#77141
function db_fetcharray($stmt) {
	$data = $stmt->result_metadata();
	$fields = array();
	$out = array();

	$fields[0] = $stmt;
	$count = 1;

	while ($field = $data->fetch_field()) {
		$fields[$count] = &$out[$field->name];
		$count++;
	}
	call_user_func_array('mysqli_stmt_bind_result', $fields);

	if (!$stmt->fetch())
		return false;
	return (count($out) == 0) ? false : $out;
}

// Utility function to perform a select and format the results as an array
// of associative arrays
function db_query($select) {
	global $db;

	$stmt = $db->prepare($select);
	$stmt->execute();
	$rv = array();

	while ($i = db_fetcharray($stmt))
		$rv[] = $i;
	$stmt->close();

	return $rv;
}

// Verify a user login and return a user info object (id, login, name).
function db_login($user, $pass) {
	global $db;

	// Verify the login/pass and get the user's ID.
	$hash = sha1($pass);
	$stmt = $db->prepare("select id,login,name from user where login=? and pwhash=?");
	$stmt->bind_param('ss', $user, $hash);
	//$stmt->bind_result($userid, $user, $name);
	$stmt->execute();
	$rv = db_fetcharray($stmt);
	$stmt->close();

	return $rv;
}

// Look up user login and return a user info object (id, login, name).
function db_user_info($user) {
	global $db;

	$stmt = $db->prepare("select id,login,name from user where login=?");
	$stmt->bind_param('s', $user);
	$stmt->execute();
	$rv = db_fetcharray($stmt);
	$stmt->close();

	return $rv;
}

// Log in a user by auth token.
function db_auth_login($auth) {
	global $db;

	// MySQL ghetto-join
	$stmt = $db->prepare("select user.id as id,user.login as login,user.name as name"
		." from user,device where user.id=device.userid and device.auth=?");
	$stmt->bind_param('s', $auth);
	$stmt->execute();
	$rv = db_fetcharray($stmt);
	$stmt->close();

	return $rv;
}

// Gets a list of GCM IDs for the specified uploading user and stream to notify.
// Stream is ignored for now. Return is array(gcmid).
function db_gcm_ids($userid, $streamid) {
	global $db;

	$stmt = $db->prepare("select device.id as id,device.gcmid as gcmid "
		. "from device where userid<>?");
	$stmt->bind_param('i', $userid);
	$stmt->execute();

	$rv = array();
	while ($i = db_fetcharray($stmt))
		$rv[] = $i['gcmid'];
	$stmt->close();
	
	return $rv;
}

// Gets a list of GCM IDs belonging to the specified user.
function db_gcm_user_ids($userid) {
	global $db;

	$stmt = $db->prepare("select device.id as id,device.gcmid as gcmid "
		. "from device where userid=?");
	$stmt->bind_param('i', $userid);
	$stmt->execute();

	$rv = array();
	while ($i = db_fetcharray($stmt))
		$rv[] = $i['gcmid'];
	$stmt->close();

	return $rv;
}

// Add a device ID for a user.
function db_auth_create($userid, $auth, $gcmid) {
	global $db;
	$stmt = $db->prepare("insert into device (userid, auth, gcmid) values (?,?,?)");
	$stmt->bind_param("iss", $userid, $auth, $gcmid);
	if (!$stmt->execute())
		echo "Error: " . $stmt->error;
	
	$stmt->close();
}

// Add or update a device ID for a user. This is to allow for
// GCM IDs passed after login, and/or a device changing user names.
function db_auth_update($userid, $auth, $gcmid) {
	global $db;

	$stmt = $db->prepare("delete from device where auth=?");
	$stmt->bind_param('s', $auth);
	$stmt->execute();
	$stmt->close();

	db_auth_create($userid, $auth, $gcmid);
}

// Delete a device ID by GCM ID.
function db_auth_delete($gcmid) {
	global $db;
	$stmt = $db->prepare("delete from device where gcmid=?");
	$stmt->bind_param("s", $gcmid);
	if (!$stmt->execute())
		echo "Error: " . $stmt->error;
	
	$stmt->close();
}

// Get a list of all user logins.
function db_user_list() {
	$rows = rows_to_assoc(db_query("select id,login from user"));
	$rv = array();
	foreach ($rows as $id => $row)
		$rv[] = $row['login'];
	return $rv;
}

// Converts rows of ('id'=>foo, ...) to a single array of ('foo'=>{...}).
function rows_to_assoc($rows) {
	$rv = array();
	foreach ($rows as $k => $r)
		$rv[$r['id']] = $r;
	return $rv;
}

// Converts rows of ('id'=>foo, 'name'=>bar) to a single array of ('foo'=>'bar').
function rows_to_idname($rows) {
	$rv = array();
	foreach ($rows as $k => $r)
		$rv[$r['id']] = $r['name'];
	return $rv;
}

// Returns an array of id=>name pairs of all the available realms.
function db_get_realms() {
	return rows_to_idname(db_query("select id,name from Realm"));
}

// Returns an array of id=>template struct pairs for all the available templates.
function db_get_templates() {
	return rows_to_assoc(db_query("select * from Template"));
}

// Returns an array of id=>template name pairs for all the available templates.
function db_get_template_names() {
	return rows_to_idname(db_get_templates());
}

// Returns an array of id=>font struct pairs for all the available fonts.
function db_get_fonts() {
	return rows_to_assoc(db_query("select * from Font"));
}

// Returns an array of id=>font name pairs for all the available fonts.
function db_get_font_names() {
	return rows_to_idname(db_get_fonts());
}

?>
