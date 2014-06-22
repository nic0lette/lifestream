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

require('db.php');
require('proto.php');
require('users.php');
require('messages.php');
header('Content-type: application/json');

// For now we simply check all the valid users for images past the cutoff date.

// Get parameters and log the user in (or die trying).
if ($_SERVER['REQUEST_METHOD'] == 'POST') {
	$auth = $_POST['auth'];
	$datestr = intval($_POST['date']);
} else {
	$auth = $_GET['auth'];
	$datestr = intval($_GET['date']);
}
if (!$auth) {
	echo proto_login_fail();
	exit;
}

$info = db_auth_login($auth);
if (!$info) {
	echo proto_login_fail();
	exit;
}

// Look for new pictures.
$thisuser = $info['login'];
$newpics = array();
$allowed_users = db_user_list();

function startsWith($haystack, $needle) {
	return strpos($haystack, $needle) === 0;
}

foreach ($allowed_users as $user) {
	if ($user == $thisuser)
		continue;
	if (startsWith($thisuser, "test") && !startsWith($user, "test"))
		continue;
	if (!startsWith($thisuser, "test") && startsWith($user, "test"))
		continue;

	$userpath = get_path($user);
	$dir = opendir($userpath);
	$peruser = array();
	while (($entry = readdir($dir))) {
		if ($entry == ".DS_Store")
			continue;
		$fn = $userpath . $entry;
		if (is_dir($fn))
			continue;
		$stats = stat($fn);
		if ($stats['mtime'] >= $datestr)
			$peruser[] = $entry;
	}
	$newpics[$user] = $peruser;
	closedir($dir);
}

$rv = array();
foreach ($newpics as $user=>$files) {
	$userpath = get_path($user);
	foreach ($files as $f) {
		$stats = stat($userpath . $f);
		$one = array('user' => $user,
			'file' => $f,
			'path' => substr($userpath,2) . $f,
			'date' => $stats['mtime']
		);
		$rv[] = $one;
	}
}

function array_sorter($a, $b) {
	if ($a['date'] == $b['date'])
		return 0;
	return ($a['date'] < $b['date']) ? -1 : 1;
}
usort($rv, "array_sorter");
echo proto_new_images($rv, get_download_msg());
exit;

?>
