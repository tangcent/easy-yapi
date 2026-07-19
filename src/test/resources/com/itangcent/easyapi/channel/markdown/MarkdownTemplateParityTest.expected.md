# Test API

## Users

---
### Get User

> BASIC

**Path:** /api/users/{id}

**Method:** GET


> REQUEST

**Path Params:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| id |  | YES | User ID |


---
### Create User

> BASIC

**Path:** /api/users

**Method:** POST


> REQUEST

**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| Content-Type | application/json | NO |  |

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| name | string | user name |
| age | int | user age |

**Request Demo:**

```json
{
  "name": "",
  "age": 0
}
```

> RESPONSE

**Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| code | int | response code |
| msg | string | message |
| data | object | response data |
| &ensp;&ensp;&#124;─id | long | user id |
| &ensp;&ensp;&#124;─name | string | user name |

**Response Demo:**

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "id": 0,
    "name": ""
  }
}
```

---
### List Users

> BASIC

**Path:** /api/users

**Method:** GET


> REQUEST

**Query:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| page |  | NO | Page number |
| size |  | NO | Page size |


> RESPONSE

**Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| code | int |  |
| data | object[] | user list |
| &ensp;&ensp;&#124;─id | long |  |
| &ensp;&ensp;&#124;─name | string |  |

**Response Demo:**

```json
{
  "code": 0,
  "data": [
    {
      "id": 0,
      "name": ""
    }
  ]
}
```

---
### Delete User

> BASIC

**Path:** /api/users/{id}

**Method:** DELETE


> REQUEST



## Misc

---
### Echo String

> BASIC

**Path:** /api/echo/string

**Method:** POST


> REQUEST

**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| Content-Type | application/json | NO |  |

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
|  | string |  |

**Request Demo:**

```json
""
```

---
### Update Metadata

> BASIC

**Path:** /api/resources/{id}/metadata

**Method:** PUT


> REQUEST

**Path Params:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| id |  | YES | Resource ID |

**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| Content-Type | application/json | NO |  |

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| metadata | map | key-value metadata |

**Request Demo:**

```json
{
  "metadata": {
    "": 0
  }
}
```

---
### Create Post

> BASIC

**Path:** /api/posts

**Method:** POST


> REQUEST

**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| Content-Type | application/json | NO |  |

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| tags | string[] | tag list |

**Request Demo:**

```json
{
  "tags": [
    ""
  ]
}
```

---
### Update Status

> BASIC

**Path:** /api/status

**Method:** PUT


> REQUEST

**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| Content-Type | application/json | NO |  |

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| status | string | status<br>active :Active user<br>inactive :Inactive user |

**Request Demo:**

```json
{
  "status": ""
}
```

---
### Upload File

> BASIC

**Path:** /api/upload

**Method:** POST


> REQUEST

**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
| Content-Type | multipart/form-data | NO |  |

**Form:**

| name | value | required | type | desc |
| ------------ | ------------ | ------------ | ------------ | ------------ |
| file |  | YES | file | File to upload |
| description |  | NO | text | File description |


---
### Get Profile

> BASIC

**Path:** /api/profile

**Method:** GET

**Desc:**

Retrieve the current user's profile information


> REQUEST



---
### Get Tree

> BASIC

**Path:** /api/tree

**Method:** GET


> REQUEST



> RESPONSE

**Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| name | string | node name |
| children | object[] | child nodes |
| &ensp;&ensp;&#124;─name | string | node name |
| &ensp;&ensp;&#124;─children | object[] | child nodes |

**Response Demo:**

```json
{
  "name": "",
  "children": [
    {
      "name": "",
      "children": [
        {        }
      ]
    }
  ]
}
```

---
### POST /api/webhook

> BASIC

**Path:** /api/webhook

**Method:** POST


> REQUEST



## gRPC

---
### GetUser

> BASIC

**Protocol:** gRPC

**Service:** UserService

**Method:** GetUser

**Streaming:** UNARY

**Full Path:** /user.UserService/GetUser


> REQUEST

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| user_id | string | user ID |

**Request Demo:**

```json
{
  "user_id": ""
}
```

> RESPONSE

**Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
| name | string | user name |

**Response Demo:**

```json
{
  "name": ""
}
```

---
### ListUsers

> BASIC

**Protocol:** gRPC

**Service:** UserService

**Method:** ListUsers

**Streaming:** SERVER_STREAMING

**Full Path:** /user.UserService/ListUsers


---
### Chat

> BASIC

**Protocol:** gRPC

**Service:** ChatService

**Method:** Chat

**Streaming:** BIDIRECTIONAL

**Full Path:** /chat.ChatService/Chat

