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
