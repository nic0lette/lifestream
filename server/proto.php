<?php

// Return for a login failure.
function proto_login_fail() {
	$result = array('message' => 'The login could not be validated.');
	return json_encode($result);
}

// Return for a login success; includes the auth token and user's full name.
function proto_login_success($token, $name) {
	$result = array('token' => $token . "", 'name' => $name);
	return json_encode($result);
}

// Return for a list of new images. $results should be an array of:
//   'user' => login, 'file' => filename, 'path' => request URL
// $message is a success message to be displayed to the user.
function proto_new_images($results, $message) {
	$result = array('images' => $results, 'message' => $message);
	return json_encode($result);
}

// Return for a failed file upload.
function proto_upload_fail() {
	$result = array('message' => 'The file could not be uploaded.');
	return json_encode($result);
}

// Return for a successful file upload. $message is a success message
// to be displayed to the user.
function proto_upload_success($message) {
	$result = array('success' => true, 'message' => $message);
	return json_encode($result);
}

?>
