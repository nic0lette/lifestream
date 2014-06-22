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

// This may be called multiple times, to add or update a GCM ID.

require('db.php');
require('proto.php');
header('Content-type: application/json');

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
	$login = $_POST['login'];
	$pass = $_POST['pass'];
	$auth = $_POST['auth'];
	$gcmid = $_POST['gcm'];
} else {
	$login = $_GET['login'];
	$pass = $_GET['pass'];
	$auth = $_GET['auth'];
	$gcmid = $_GET['gcm'];
}

if (!$auth) {
	echo proto_login_fail();
	exit;
}

$info = db_login($login, $pass);
if (!$info) {
	echo proto_login_fail();
	exit;
}

db_auth_update($info['id'], $auth, $gcmid);

echo proto_login_success($auth, $info['name']);

?>
