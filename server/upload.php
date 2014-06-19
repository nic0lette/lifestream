<?php

require('users.php');
require('db.php');
require('proto.php');
require('messages.php');
header('Content-type: application/json');

if ($_SERVER['REQUEST_METHOD'] == 'POST') {
	$auth = $_POST['auth'];
} else {
	$auth = $_GET['auth'];
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

$user_path = $info['login'];
if (!$user_path) {
	echo proto_login_fail();
	exit;
}

$target_path  = "./files/" . $user_path . "/";
$target_path = $target_path . basename( $_FILES['uploadedfile']['name']);
if (!move_uploaded_file($_FILES['uploadedfile']['tmp_name'], $target_path)) {
	echo proto_upload_fail();
	exit;
}

echo proto_upload_success(get_upload_msg());
// exit;


// Now we also need to talk to GCM to let it know something happened. Find a
// list of devices we want to broadcast to, and do it.

// http://stackoverflow.com/questions/11242743/gcm-with-php-google-cloud-messaging

// Replace with real BROWSER API key from Google APIs
$apiKey = $api_key;

// Replace with real client registration IDs 
$registrationIDs = db_gcm_ids($info['id'], '');

// Message to be sent
$message = "x";

// Set POST variables
$url = 'https://android.googleapis.com/gcm/send';

$fields = array(
	'registration_ids'	=> $registrationIDs,
	'data'			=> array( "message" => $message ),
	'time_to_live'		=> (60*60),
	'collapse_key'		=> 'newpic'
);

$headers = array( 
	'Authorization: key=' . $apiKey,
        'Content-Type: application/json'
);

// Open connection
$ch = curl_init();

// Set the url, number of POST vars, POST data
curl_setopt($ch, CURLOPT_URL, $url);

curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);

curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($fields));

// Execute post
$result = curl_exec($ch);

$log = fopen("logs/gcm.log", "a");
fwrite($log, $result . "\n\n-----\n\n");
fclose($log);

// Close connection
curl_close($ch);

?>
