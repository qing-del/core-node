# 个人SaaS中台项目


**简介**:个人SaaS中台项目


**HOST**:http://localhost:8080


**联系人**:


**Version**:0.0.1


**接口路径**:/v3/api-docs/admin 端接口


[TOC]






# Admin-审核管理


## 批量审核笔记


**接口地址**:`/admin/audit/note/review/batch`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量审核笔记申请，仅处理待审核记录并同步回写 biz_note 的 is_pass，返回实际处理数量。</p>



**请求示例**:


```javascript
{
  "ids": [],
  "status": 0,
  "rejectReason": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|auditBatchReviewDTO|批量审核请求（审核记录ID列表及审核结果）|body|true|AuditBatchReviewDTO|AuditBatchReviewDTO|
|&emsp;&emsp;ids|||false|array|integer(int64)|
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;rejectReason|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInteger|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||integer(int32)|integer(int32)|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": 0
}
```


## 批量审核主题-标签


**接口地址**:`/admin/audit/meta/review/batch`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量审核元数据申请，仅处理待审核记录并同步回写 biz_topic/biz_tag 的 is_pass，返回实际处理数量。</p>



**请求示例**:


```javascript
{
  "ids": [],
  "status": 0,
  "rejectReason": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|auditBatchReviewDTO|批量审核请求（审核记录ID列表及审核结果）|body|true|AuditBatchReviewDTO|AuditBatchReviewDTO|
|&emsp;&emsp;ids|||false|array|integer(int64)|
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;rejectReason|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInteger|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||integer(int32)|integer(int32)|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": 0
}
```


## 批量审核图片


**接口地址**:`/admin/audit/image/review/batch`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量审核图片申请，仅处理待审核记录并同步回写 biz_image 的 is_pass，返回实际处理数量。</p>



**请求示例**:


```javascript
{
  "ids": [],
  "status": 0,
  "rejectReason": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|auditBatchReviewDTO|批量审核请求（审核记录ID列表及审核结果）|body|true|AuditBatchReviewDTO|AuditBatchReviewDTO|
|&emsp;&emsp;ids|||false|array|integer(int64)|
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;rejectReason|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInteger|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||integer(int32)|integer(int32)|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": 0
}
```


## 分页查询笔记审核记录


**接口地址**:`/admin/audit/note/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按审核状态和申请人筛选笔记审核记录，按 update_time 倒序返回。</p>



**请求示例**:


```javascript
{
  "status": 0,
  "applicantUserId": 0,
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteAuditListDTO|笔记审核查询条件（审核状态、申请人）|body|true|NoteAuditListDTO|NoteAuditListDTO|
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;applicantUserId|||false|integer(int64)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 分页查询主题-标签审核记录


**接口地址**:`/admin/audit/meta/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按申请类型、审核状态和申请人筛选元数据审核记录，按 update_time 倒序返回。</p>



**请求示例**:


```javascript
{
  "applyType": 0,
  "status": 0,
  "applicantUserId": 0,
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|metaAuditListDTO|元数据审核查询条件（申请类型、审核状态、申请人）|body|true|MetaAuditListDTO|MetaAuditListDTO|
|&emsp;&emsp;applyType|||false|integer(int32)||
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;applicantUserId|||false|integer(int64)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 分页查询图片审核记录


**接口地址**:`/admin/audit/image/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按审核状态和申请人筛选图片审核记录，按 update_time 倒序返回。</p>



**请求示例**:


```javascript
{
  "status": 0,
  "applicantUserId": 0,
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageAuditListDTO|图片审核查询条件（审核状态、申请人）|body|true|ImageAuditListDTO|ImageAuditListDTO|
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;applicantUserId|||false|integer(int64)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


# Admin-笔记关联管理


## 查询反向引用笔记 (Admin)


**接口地址**:`/admin/note/relation/backlinks/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按笔记 ID 反查所有引用了它的源笔记。管理端不过滤所有权与发布状态，返回所有未删除的引用关系。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListNoteBacklinkVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|NoteBacklinkVO|
|&emsp;&emsp;sourceNoteId|源笔记 ID|integer(int64)||
|&emsp;&emsp;sourceNoteTitle|源笔记标题|string||
|&emsp;&emsp;parsedNoteName|源笔记链接解析后的标题|string||
|&emsp;&emsp;anchor|锚点|string||
|&emsp;&emsp;nickname|解析别名|string||
|&emsp;&emsp;isCrossUser|是否跨用户|integer(int32)||
|&emsp;&emsp;sourceNoteStatus|源笔记状态|integer(int32)||
|&emsp;&emsp;createTime|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"sourceNoteId": 0,
			"sourceNoteTitle": "",
			"parsedNoteName": "",
			"anchor": "",
			"nickname": "",
			"isCrossUser": 0,
			"sourceNoteStatus": 0,
			"createTime": ""
		}
	]
}
```


## 查询标签反向引用笔记 (Admin)


**接口地址**:`/admin/note/relation/backlinks/tag/{tagId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按标签 ID 反查所有引用了它的源笔记。管理端不过滤所有权与发布状态，返回所有未删除的引用关系。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|tagId|标签ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListTagBacklinkVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|TagBacklinkVO|
|&emsp;&emsp;sourceNoteId|源笔记 ID|integer(int64)||
|&emsp;&emsp;sourceNoteTitle|源笔记标题|string||
|&emsp;&emsp;parsedTagName|源笔记中解析出的标签名称|string||
|&emsp;&emsp;isCrossUser|是否跨用户|integer(int32)||
|&emsp;&emsp;sourceNoteStatus|源笔记状态|integer(int32)||
|&emsp;&emsp;createTime|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"sourceNoteId": 0,
			"sourceNoteTitle": "",
			"parsedTagName": "",
			"isCrossUser": 0,
			"sourceNoteStatus": 0,
			"createTime": ""
		}
	]
}
```


## 查询图片反向引用笔记 (Admin)


**接口地址**:`/admin/note/relation/backlinks/image/{imageId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按图片 ID 反查所有引用了它的源笔记。管理端不过滤所有权与发布状态，返回所有未删除的引用关系。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageId|图片ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListImageBacklinkVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ImageBacklinkVO|
|&emsp;&emsp;sourceNoteId|源笔记 ID|integer(int64)||
|&emsp;&emsp;sourceNoteTitle|源笔记标题|string||
|&emsp;&emsp;parsedImageName|源笔记中解析出的图片名称|string||
|&emsp;&emsp;isCrossUser|是否跨用户|integer(int32)||
|&emsp;&emsp;sourceNoteStatus|源笔记状态|integer(int32)||
|&emsp;&emsp;createTime|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"sourceNoteId": 0,
			"sourceNoteTitle": "",
			"parsedImageName": "",
			"isCrossUser": 0,
			"sourceNoteStatus": 0,
			"createTime": ""
		}
	]
}
```


# Admin-标签管理


## 修改标签


**接口地址**:`/admin/tag/modify`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>修改标签名称前会先按 userId 校验标签归属与存在性，并再次检查新名称是否与当前用户已有标签冲突。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "tagName": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|tagModifyDTO|标签修改请求（标签ID、新名称）|body|true|TagModifyDTO|TagModifyDTO|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;tagName|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 分页查询标签


**接口地址**:`/admin/tag/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按关键词前缀进行模糊匹配并分页返回标签列表，支持按当前用户或指定用户维度查询。</p>



**请求示例**:


```javascript
{
  "userId": 0,
  "keyword": "",
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|tagQueryDTO|标签查询条件（关键词、用户ID、分页参数）|body|true|TagQueryDTO|TagQueryDTO|
|&emsp;&emsp;userId|||false|integer(int64)||
|&emsp;&emsp;keyword|||false|string||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 批量删除标签


**接口地址**:`/admin/tag/delete`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>批量删除前会先检查所有目标标签是否存在，并查询其被笔记引用的数量；只要有任一标签仍被使用，整批删除即拒绝执行。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids|标签ID，使用英文逗号分隔，例如 1,2,3|query|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


# Admin-用户管理


## 获取用户信息


**接口地址**:`/admin/user/user`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据用户ID获取单个用户的详细信息。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|用户ID|query|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultUserEntity|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||UserEntity|UserEntity|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;username||string||
|&emsp;&emsp;password||string||
|&emsp;&emsp;nickname||string||
|&emsp;&emsp;email||string||
|&emsp;&emsp;roleId||integer(int64)||
|&emsp;&emsp;status||integer(int32)||
|&emsp;&emsp;maxStorageBytes||integer(int64)||
|&emsp;&emsp;usedStorageBytes||integer(int64)||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"username": "",
		"password": "",
		"nickname": "",
		"email": "",
		"roleId": 0,
		"status": 0,
		"maxStorageBytes": 0,
		"usedStorageBytes": 0,
		"createTime": "",
		"updateTime": ""
	}
}
```


## 管理员新增账户


**接口地址**:`/admin/user/user`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>管理员直接创建新用户账户，无需注册流程。</p>



**请求示例**:


```javascript
{
  "username": "",
  "password": "",
  "roleId": 0,
  "nickname": "",
  "email": "",
  "status": 0,
  "maxStorageBytes": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userAddDTO|新增用户请求，包含用户名、密码和角色信息|body|true|UserAddDTO|UserAddDTO|
|&emsp;&emsp;username|||true|string||
|&emsp;&emsp;password|||false|string||
|&emsp;&emsp;roleId|||false|integer(int64)||
|&emsp;&emsp;nickname|||false|string||
|&emsp;&emsp;email|||false|string||
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;maxStorageBytes|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 修改用户信息


**接口地址**:`/admin/user/user`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>修改指定用户的基本信息、权限或密码，需要上级角色权限。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "username": "",
  "nickname": "",
  "email": "",
  "roleId": 0,
  "status": 0,
  "newPassword": "",
  "confirmPassword": "",
  "maxStorageBytes": 0,
  "password": "",
  "targetUserId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userModifyDTO|用户修改请求，包含目标用户ID和需要修改的字段|body|true|UserModifyDTO|UserModifyDTO|
|&emsp;&emsp;id|||true|integer(int64)||
|&emsp;&emsp;username|||false|string||
|&emsp;&emsp;nickname|||false|string||
|&emsp;&emsp;email|||false|string||
|&emsp;&emsp;roleId|||false|integer(int64)||
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;newPassword|||false|string||
|&emsp;&emsp;confirmPassword|||false|string||
|&emsp;&emsp;maxStorageBytes|||false|integer(int64)||
|&emsp;&emsp;password|||false|string||
|&emsp;&emsp;targetUserId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 批量删除账户


**接口地址**:`/admin/user/user`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按用户ID列表批量删除用户账户。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids|待删除的用户ID列表|query|true|array|integer|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 封禁-解封账号


**接口地址**:`/admin/user/status/{status}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>设置用户账号的启用/禁用状态，需要上级角色权限。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "targetUserId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|status|目标状态（1:启用, 0:禁用）|path|true|integer(int32)||
|userStatusDTO|包含目标用户ID的请求体|body|true|UserStatusDTO|UserStatusDTO|
|&emsp;&emsp;id|||true|integer(int64)||
|&emsp;&emsp;targetUserId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 退出登录


**接口地址**:`/admin/user/logout`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>管理员退出登录，删除 Redis 中的 JWT 令牌。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|Result|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 管理员登录


**接口地址**:`/admin/user/login`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>验证管理员账号密码，登录成功后签发 JWT 令牌并返回；后续管理员接口通过该 token 鉴权。</p>



**请求示例**:


```javascript
{
  "username": "",
  "password": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userLoginDTO|管理员登录请求，包含用户名和密码|body|true|UserLoginDTO|UserLoginDTO|
|&emsp;&emsp;username|||true|string||
|&emsp;&emsp;password|||true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 分页查询用户列表


**接口地址**:`/admin/user/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按用户名、角色等条件分页查询用户列表，返回分页结果供管理端展示。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "username": "",
  "status": 0,
  "roleId": 0,
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userListDTO|用户列表查询条件，包含分页参数和筛选字段|body|true|UserListDTO|UserListDTO|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;username|||false|string||
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;roleId|||false|integer(int64)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 获取当前用户信息


**接口地址**:`/admin/user/me`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>从 JWT 中解析当前用户ID，查询并返回用户详情（不含密码等敏感字段）。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultUserEntity|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||UserEntity|UserEntity|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;username||string||
|&emsp;&emsp;password||string||
|&emsp;&emsp;nickname||string||
|&emsp;&emsp;email||string||
|&emsp;&emsp;roleId||integer(int64)||
|&emsp;&emsp;status||integer(int32)||
|&emsp;&emsp;maxStorageBytes||integer(int64)||
|&emsp;&emsp;usedStorageBytes||integer(int64)||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"username": "",
		"password": "",
		"nickname": "",
		"email": "",
		"roleId": 0,
		"status": 0,
		"maxStorageBytes": 0,
		"usedStorageBytes": 0,
		"createTime": "",
		"updateTime": ""
	}
}
```


# Admin-邮件管理


## 发送自定义邮件


**接口地址**:`/admin/email/send`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>管理员可指定单个用户或按角色群发，邮件内容可以为纯文本或 HTML</p>



**请求示例**:


```javascript
{
  "userId": 0,
  "roleId": 0,
  "subject": "",
  "body": "",
  "templateName": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|emailSendDTO|EmailSendDTO|body|true|EmailSendDTO|EmailSendDTO|
|&emsp;&emsp;userId|||false|integer(int64)||
|&emsp;&emsp;roleId|||false|integer(int32)||
|&emsp;&emsp;subject|||true|string||
|&emsp;&emsp;body|||true|string||
|&emsp;&emsp;templateName|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultEmailResultDTO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||EmailResultDTO|EmailResultDTO|
|&emsp;&emsp;successCount||integer(int32)||
|&emsp;&emsp;failCount||integer(int32)||
|&emsp;&emsp;message||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"successCount": 0,
		"failCount": 0,
		"message": ""
	}
}
```


# Admin-音频任务


## 分页查询任务列表


**接口地址**:`/admin/audio/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按任务状态、任务创建时间分页查询任务列表。</p>



**请求示例**:


```javascript
{
  "userId": 0,
  "pageNum": 0,
  "pageSize": 0,
  "status": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|audioTaskPageQueryDTO|分页参数|body|true|AudioTaskPageQueryDTO|AudioTaskPageQueryDTO|
|&emsp;&emsp;userId|||false|integer(int64)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;status|任务状态筛选（null=不过滤）||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


# Admin-笔记管理


## 修改笔记元信息


**接口地址**:`/admin/note/info`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>修改笔记描述和主题等基础元数据，不变更标题和 Markdown 正文；修改前会校验目标主题有效性和同主题同名唯一性。</p>



**请求示例**:


```javascript
{
  "id": 1,
  "topicId": 1,
  "description": "更新后的描述"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteModifyInfoDTO|笔记元信息修改请求（笔记ID、新描述、新主题ID）|body|true|NoteModifyInfoDTO|NoteModifyInfoDTO|
|&emsp;&emsp;id|笔记ID||true|integer(int64)||
|&emsp;&emsp;topicId|主题ID||false|integer(int64)||
|&emsp;&emsp;description|笔记描述||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 强制设置笔记状态（暂不支持）


**接口地址**:`/admin/note/force/{status}/{noteId}`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>预留接口，当前暂不支持强制设置笔记状态，调用时将抛出异常。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|status|存在的笔记状态之一即可|path|true|integer(int64)||
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|Result|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 分页查询笔记


**接口地址**:`/admin/note/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按用户、主题、标题、发布状态、审核状态和缺失状态等条件分页查询笔记列表，并按创建时间倒序返回。</p>



**请求示例**:


```javascript
{
  "userId": 0,
  "topicId": 0,
  "title": "",
  "status": 0,
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteQueryDTO|笔记查询条件（用户ID、主题ID、标题关键词、发布状态、审核状态）|body|true|NoteQueryDTO|NoteQueryDTO|
|&emsp;&emsp;userId|||false|integer(int64)||
|&emsp;&emsp;topicId|||false|integer(int64)||
|&emsp;&emsp;title|||false|string||
|&emsp;&emsp;status|||false|integer(int32)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 转换笔记为 HTML


**接口地址**:`/admin/note/convert/{noteId}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>转换前先校验笔记不存在缺失关联信息，然后调用 MarkdownHtmlEngine 生成前置元信息、TOC 和正文 HTML，并将结果写入转换缓存表，供前端阅读页直接渲染。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|Result|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 删除笔记转换缓存


**接口地址**:`/admin/note/convert/{noteId}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>删除指定笔记的转换缓存记录，同时将发布状态重置为待转换，避免前端继续读取失效内容。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|Result|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 获取笔记 Markdown 源内容


**接口地址**:`/admin/note/source/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>直接读取数据库中的笔记原文并以纯文本形式返回，供编辑器回显或二次编辑。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 打开笔记内容


**接口地址**:`/admin/note/open/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>读取指定笔记的已转换结果（TOC + 正文 HTML），管理端用作查看他人笔记的接口，用于前端阅读页渲染。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteConvertResultVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteConvertResultVO|NoteConvertResultVO|
|&emsp;&emsp;meta|标题、标签、创建时间|NoteConvertMetaVO|NoteConvertMetaVO|
|&emsp;&emsp;&emsp;&emsp;title||string||
|&emsp;&emsp;&emsp;&emsp;tags||array|string|
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;tocHtml|目录 HTML - (可以用于创建标题跳转栏)|string||
|&emsp;&emsp;bodyHtml|正文 HTML|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"meta": {
			"title": "",
			"tags": [],
			"createTime": ""
		},
		"tocHtml": "",
		"bodyHtml": ""
	}
}
```


## 查询笔记详情


**接口地址**:`/admin/note/info/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回笔记基础元数据，并聚合标签、图片、双链映射及已转换内容，供前端详情页一次性加载。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteDetailVO|NoteDetailVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;userId||integer(int64)||
|&emsp;&emsp;topicId||integer(int64)||
|&emsp;&emsp;topicName||string||
|&emsp;&emsp;title||string||
|&emsp;&emsp;description||string||
|&emsp;&emsp;storageType||integer(int32)||
|&emsp;&emsp;status||integer(int32)||
|&emsp;&emsp;missingInfoMask||integer(int32)||
|&emsp;&emsp;missingCount||integer(int32)||
|&emsp;&emsp;mdFileSize||integer(int64)||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||
|&emsp;&emsp;tags||array|string|
|&emsp;&emsp;images||array|ImageSimpleVO|
|&emsp;&emsp;&emsp;&emsp;imageId||integer||
|&emsp;&emsp;&emsp;&emsp;noteId||integer||
|&emsp;&emsp;&emsp;&emsp;parsedImageName||string||
|&emsp;&emsp;&emsp;&emsp;filename||string||
|&emsp;&emsp;&emsp;&emsp;ossUrl||string||
|&emsp;&emsp;&emsp;&emsp;isPublic||integer||
|&emsp;&emsp;&emsp;&emsp;isPass||integer||
|&emsp;&emsp;&emsp;&emsp;isCrossUser||integer||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;eachNotes||array|NoteEachSimpleVO|
|&emsp;&emsp;&emsp;&emsp;targetNoteId||integer||
|&emsp;&emsp;&emsp;&emsp;targetNoteTitle||string||
|&emsp;&emsp;&emsp;&emsp;parsedNoteName||string||
|&emsp;&emsp;&emsp;&emsp;anchor||string||
|&emsp;&emsp;&emsp;&emsp;nickname||string||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||
|&emsp;&emsp;converted||NoteConvertResultVO|NoteConvertResultVO|
|&emsp;&emsp;&emsp;&emsp;meta|标题、标签、创建时间|NoteConvertMetaVO|NoteConvertMetaVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;title||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;tags||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;tocHtml|目录 HTML - (可以用于创建标题跳转栏)|string||
|&emsp;&emsp;&emsp;&emsp;bodyHtml|正文 HTML|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"userId": 0,
		"topicId": 0,
		"topicName": "",
		"title": "",
		"description": "",
		"storageType": 0,
		"status": 0,
		"missingInfoMask": 0,
		"missingCount": 0,
		"mdFileSize": 0,
		"createTime": "",
		"updateTime": "",
		"tags": [],
		"images": [
			{
				"imageId": 0,
				"noteId": 0,
				"parsedImageName": "",
				"filename": "",
				"ossUrl": "",
				"isPublic": 0,
				"isPass": 0,
				"isCrossUser": 0,
				"isMissing": 0,
				"createTime": ""
			}
		],
		"eachNotes": [
			{
				"targetNoteId": 0,
				"targetNoteTitle": "",
				"parsedNoteName": "",
				"anchor": "",
				"nickname": "",
				"isMissing": 0
			}
		],
		"converted": {
			"meta": {
				"title": "",
				"tags": [],
				"createTime": ""
			},
			"tocHtml": "",
			"bodyHtml": ""
		}
	}
}
```


## 批量删除笔记


**接口地址**:`/admin/note/delete`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>批量删除笔记主记录并同步清理转换结果、Diff、内容和三类关联映射，随后回收当前用户已占用的存储空间。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids|笔记ID，使用英文逗号分隔|query|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


# Admin-图片管理


## 迁移图片到云存储


**接口地址**:`/admin/image/transfer-to-cloud`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>批量触发图片存储介质迁移流程，默认处理阿里云 OSS 入口并预留 R2 等多云扩展；按图片 ID 逐条处理，失败项会记录日志。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids|图片ID列表，使用英文逗号分隔|query|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 修改图片元信息


**接口地址**:`/admin/image/modify-info`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>修改图片名称或主题归属等元信息，不替换图片二进制内容；修改文件名时会做同用户同主题唯一性校验。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "topicId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageModifyInfoDTO|图片元信息修改请求（图片ID、新名称、新主题ID）|body|true|ImageModifyInfoDTO|ImageModifyInfoDTO|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;topicId|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 审核图片


**接口地址**:`/admin/image/audit/review`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>管理员根据审核申请执行通过或拒绝；通过时将图片审核状态置为通过，拒绝时必须给出拒绝原因并同步回写审核记录。(开发的时候不要调用这个接口，因为在认证控制器里面已经有图片审核的接口了)</p>



**请求示例**:


```javascript
{
  "auditId": 0,
  "approved": true,
  "rejectReason": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageAuditReviewDTO|图片审核请求（审核ID、是否通过、拒绝原因）|body|true|ImageAuditReviewDTO|ImageAuditReviewDTO|
|&emsp;&emsp;auditId|||false|integer(int64)||
|&emsp;&emsp;approved|||false|boolean||
|&emsp;&emsp;rejectReason|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 设置图片公开状态


**接口地址**:`/admin/image/public/{isPublic}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>切换图片公开/私有状态，修改后会影响跨用户复用和前端可见范围。</p>



**请求示例**:


```javascript
{
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|isPublic|是否公开（0:私有, 1:公开）|path|true|integer(int32)||
|imagePublicDTO|图片公开状态请求（图片ID）|body|true|ImagePublicDTO|ImagePublicDTO|
|&emsp;&emsp;id|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 分页查询图片


**接口地址**:`/admin/image/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按用户、主题、文件名、公开状态和审核状态等条件分页查询图片列表，便于管理端筛选与审核。</p>



**请求示例**:


```javascript
{
  "userId": 0,
  "topicId": 0,
  "filename": "",
  "storageType": 0,
  "isPublic": 0,
  "isPass": 0,
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageQueryDTO|图片查询条件（用户ID、主题ID、文件名、公开状态、审核状态）|body|true|ImageQueryDTO|ImageQueryDTO|
|&emsp;&emsp;userId|||false|integer(int64)||
|&emsp;&emsp;topicId|||false|integer(int64)||
|&emsp;&emsp;filename|||false|string||
|&emsp;&emsp;storageType|||false|integer(int32)||
|&emsp;&emsp;isPublic|||false|integer(int32)||
|&emsp;&emsp;isPass|||false|integer(int32)||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 查询图片关联笔记


**接口地址**:`/admin/image/notes/{imageId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>查询当前图片被哪些笔记引用，返回笔记简要信息，便于评估删除、迁移或审核影响。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageId|图片ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListNoteSimpleVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|NoteSimpleVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;title||string||
|&emsp;&emsp;isCrossUser||integer(int32)||
|&emsp;&emsp;status||integer(int32)||
|&emsp;&emsp;createTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"title": "",
			"isCrossUser": 0,
			"status": 0,
			"createTime": ""
		}
	]
}
```


## 批量删除图片


**接口地址**:`/admin/image/delete`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>批量删除图片前会先检查是否被笔记引用，若存在引用则整批拒绝；删除成功后会同步回收用户存储并记录死信队列。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids|图片ID列表，使用英文逗号分隔|query|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultImageBatchDeleteVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||ImageBatchDeleteVO|ImageBatchDeleteVO|
|&emsp;&emsp;successIds||array|integer(int64)|
|&emsp;&emsp;successFileNames||array|string|
|&emsp;&emsp;failIds||array|integer(int64)|
|&emsp;&emsp;failFileNames||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"successIds": [],
		"successFileNames": [],
		"failIds": [],
		"failFileNames": []
	}
}
```


# Admin-主题管理


## 分页查询主题


**接口地址**:`/admin/topic/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按当前用户或指定用户、关键字和分页参数查询主题列表，返回结果按服务层默认排序规则展示。</p>



**请求示例**:


```javascript
{
  "userId": 0,
  "keyword": "",
  "pageNum": 0,
  "pageSize": 0,
  "sortBy": "",
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|topicListDTO|主题查询条件（用户ID、关键词、分页参数）|body|true|TopicListDTO|TopicListDTO|
|&emsp;&emsp;userId|||false|integer(int64)||
|&emsp;&emsp;keyword|||false|string||
|&emsp;&emsp;pageNum|||false|integer(int32)||
|&emsp;&emsp;pageSize|||false|integer(int32)||
|&emsp;&emsp;sortBy|||false|string||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 查询主题详情


**接口地址**:`/admin/topic/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据主题 ID 读取单条主题信息，用于编辑页回显或详情展示；若主题不存在则按业务规则返回不存在。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|主题ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultTopicDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||TopicDetailVO|TopicDetailVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;topicName||string||
|&emsp;&emsp;sortOrder||integer(int32)||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"topicName": "",
		"sortOrder": 0,
		"createTime": "",
		"updateTime": ""
	}
}
```


## 批量删除主题


**接口地址**:`/admin/topic/delete`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>批量删除前会先验证所有主题是否存在，并检查每个主题下是否存在未删除笔记；只要存在引用，整批删除即拒绝。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids|主题ID，使用英文逗号分隔，例如 1,2,3|query|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```