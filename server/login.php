<?php

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
