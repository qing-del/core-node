# 个人SaaS中台项目


**简介**:个人SaaS中台项目


**HOST**:http://localhost:8080


**联系人**:


**Version**:0.0.1


**接口路径**:/v3/api-docs/user 端接口


[TOC]






# User-主题管理


## 修改主题


**接口地址**:`/user/topic/modify`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>修改主题的排序等级。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "sortOrder": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|topicModifyDTO|修改主题请求（主题ID、排序等级）|body|true|TopicModifyDTO|TopicModifyDTO|
|&emsp;&emsp;id|||true|integer(int64)||
|&emsp;&emsp;sortOrder|||false|integer(int32)||


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


## 发起主题审核申请


**接口地址**:`/user/topic/submitAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>传入主题 ID，发起对该主题的审核申请。仅允许申请审核自己的主题，且该主题不能已通过审核或已有待审核申请。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|主题ID|query|true|integer(int64)||


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


## 条件查询主题


**接口地址**:`/user/topic/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>查询当前用户自己的主题 + 别人已通过审核的主题。支持按关键字模糊搜索，分页返回。</p>



**请求示例**:


```javascript
{
  "keyword": "",
  "scope": "",
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userTopicQueryDTO|主题查询条件（关键词、分页参数）|body|true|UserTopicQueryDTO|UserTopicQueryDTO|
|&emsp;&emsp;keyword|||false|string||
|&emsp;&emsp;scope|||false|string||
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


## 撤销主题审核申请


**接口地址**:`/user/topic/cancelAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>撤销当前用户的主题审核申请，仅删除待审核记录。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|主题ID|query|true|integer(int64)||


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


## 新增主题


**接口地址**:`/user/topic/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>从当前登录用户上下文获取 userId 后创建主题；服务层会先清洗主题名、校验长度，再检查同一用户下主题名称唯一性。</p>



**请求示例**:


```javascript
{
  "topicName": "",
  "sortOrder": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|topicAddDTO|新增主题请求（主题名称）|body|true|TopicAddDTO|TopicAddDTO|
|&emsp;&emsp;topicName|||true|string||
|&emsp;&emsp;sortOrder|||false|integer(int32)||


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


## 获取用户主题统计


**接口地址**:`/user/topic/stats`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回当前用户的主题总数和已通过审核数。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultTopicStatsVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||TopicStatsVO|TopicStatsVO|
|&emsp;&emsp;topicCount|Total topic count of current user|integer(int64)||
|&emsp;&emsp;passedCount|Passed topic count of current user|integer(int64)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"topicCount": 0,
		"passedCount": 0
	}
}
```


## 批量删除主题


**接口地址**:`/user/topic/delete`


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


# User-邮箱管理


## 验证邮箱修改码


**接口地址**:`/user/email/verify-change`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>使用新邮箱中收到的6位验证码完成邮箱修改</p>



**请求参数**:


暂无


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


## 重新发送激活邮件


**接口地址**:`/user/email/resend-activation`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>向当前用户注册邮箱再次发送激活链接</p>



**请求参数**:


暂无


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


## 发起邮箱修改


**接口地址**:`/user/email/change-code`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>验证原邮箱后向新邮箱发送6位验证码</p>



**请求示例**:


```javascript
{
  "oldEmail": "",
  "newEmail": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|emailChangeRequestDTO|原邮箱和新邮箱地址|body|true|EmailChangeRequestDTO|EmailChangeRequestDTO|
|&emsp;&emsp;oldEmail|||true|string||
|&emsp;&emsp;newEmail|||true|string||


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


## 获取邮箱与激活状态


**接口地址**:`/user/email/status`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回当前用户的邮箱地址与账号激活状态</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultMapStringObject|


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


# User-笔记关联管理


## 绑定标签映射


**接口地址**:`/user/note/relation/tag/bind`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>为指定标签映射行绑定目标标签，绑定前会校验名称一致性与标签审核状态，成功后刷新笔记缺失信息。</p>



**请求示例**:


```javascript
{
  "mappingId": 0,
  "tagId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|tagMappingBindDTO|标签绑定请求（映射行ID、目标标签ID）|body|true|TagMappingBindDTO|TagMappingBindDTO|
|&emsp;&emsp;mappingId|||true|integer(int64)||
|&emsp;&emsp;tagId|||true|integer(int64)||


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


## 绑定图片映射


**接口地址**:`/user/note/relation/image/bind`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>为指定图片映射行绑定目标图片，绑定前会校验文件名一致性和图片审核状态，同时计算是否跨用户引用。</p>



**请求示例**:


```javascript
{
  "mappingId": 0,
  "imageId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|imageMappingBindDTO|图片绑定请求（映射行ID、目标图片ID）|body|true|ImageMappingBindDTO|ImageMappingBindDTO|
|&emsp;&emsp;mappingId|||true|integer(int64)||
|&emsp;&emsp;imageId|||true|integer(int64)||


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


## 绑定双链笔记映射


**接口地址**:`/user/note/relation/each/bind`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>为指定双链映射行绑定目标笔记，绑定前会校验标题匹配、目标笔记审核状态和删除状态，成功后刷新缺失信息。</p>



**请求示例**:


```javascript
{
  "mappingId": 0,
  "noteId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|eachMappingBindDTO|双链绑定请求（映射行ID、目标笔记ID）|body|true|EachMappingBindDTO|EachMappingBindDTO|
|&emsp;&emsp;mappingId|||true|integer(int64)||
|&emsp;&emsp;noteId|||true|integer(int64)||


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


## 校验关联完整性


**接口地址**:`/user/note/relation/check/{noteId}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>遍历笔记的标签、图片和双链三类映射，判断是否都已完整绑定且审核通过，会自动转换笔记状态；如果收到的结果中<code>isCompeted</code>这个值不为true即为缺失信息转换失败。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteCheckBindingVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteCheckBindingVO|NoteCheckBindingVO|
|&emsp;&emsp;noteId||integer(int64)||
|&emsp;&emsp;status||integer(int32)||
|&emsp;&emsp;statusDesc||string||
|&emsp;&emsp;missingInfoMask||integer(int32)||
|&emsp;&emsp;missingCount||integer(int32)||
|&emsp;&emsp;missingTags||array|string|
|&emsp;&emsp;missingImages||array|string|
|&emsp;&emsp;missingNoteNames||array|string|
|&emsp;&emsp;complete||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"noteId": 0,
		"status": 0,
		"statusDesc": "",
		"missingInfoMask": 0,
		"missingCount": 0,
		"missingTags": [],
		"missingImages": [],
		"missingNoteNames": [],
		"complete": true
	}
}
```


## 查询笔记关联映射


**接口地址**:`/user/note/relation/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>查询笔记与标签、图片、双链笔记三类关联的全部映射行、绑定状态和缺失标记，用于编辑器联动展示。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteRelationDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteRelationDetailVO|NoteRelationDetailVO|
|&emsp;&emsp;noteId||integer(int64)||
|&emsp;&emsp;tags||array|NoteTagMappingRowVO|
|&emsp;&emsp;&emsp;&emsp;mappingId||integer||
|&emsp;&emsp;&emsp;&emsp;noteId||integer||
|&emsp;&emsp;&emsp;&emsp;tagId||integer||
|&emsp;&emsp;&emsp;&emsp;parsedTagName||string||
|&emsp;&emsp;&emsp;&emsp;tagName||string||
|&emsp;&emsp;&emsp;&emsp;isPass||integer||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||
|&emsp;&emsp;images||array|NoteImageMappingRowVO|
|&emsp;&emsp;&emsp;&emsp;mappingId||integer||
|&emsp;&emsp;&emsp;&emsp;noteId||integer||
|&emsp;&emsp;&emsp;&emsp;imageId||integer||
|&emsp;&emsp;&emsp;&emsp;parsedImageName||string||
|&emsp;&emsp;&emsp;&emsp;filename||string||
|&emsp;&emsp;&emsp;&emsp;isCrossUser||integer||
|&emsp;&emsp;&emsp;&emsp;isPass||integer||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||
|&emsp;&emsp;eachNotes||array|NoteEachMappingRowVO|
|&emsp;&emsp;&emsp;&emsp;mappingId||integer||
|&emsp;&emsp;&emsp;&emsp;sourceNoteId||integer||
|&emsp;&emsp;&emsp;&emsp;targetNoteId||integer||
|&emsp;&emsp;&emsp;&emsp;parsedNoteName||string||
|&emsp;&emsp;&emsp;&emsp;targetNoteTitle||string||
|&emsp;&emsp;&emsp;&emsp;anchor||string||
|&emsp;&emsp;&emsp;&emsp;nickname||string||
|&emsp;&emsp;&emsp;&emsp;isPass||integer||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"noteId": 0,
		"tags": [
			{
				"mappingId": 0,
				"noteId": 0,
				"tagId": 0,
				"parsedTagName": "",
				"tagName": "",
				"isPass": 0,
				"isMissing": 0
			}
		],
		"images": [
			{
				"mappingId": 0,
				"noteId": 0,
				"imageId": 0,
				"parsedImageName": "",
				"filename": "",
				"isCrossUser": 0,
				"isPass": 0,
				"isMissing": 0
			}
		],
		"eachNotes": [
			{
				"mappingId": 0,
				"sourceNoteId": 0,
				"targetNoteId": 0,
				"parsedNoteName": "",
				"targetNoteTitle": "",
				"anchor": "",
				"nickname": "",
				"isPass": 0,
				"isMissing": 0
			}
		]
	}
}
```


## 查询笔记关联图片


**接口地址**:`/user/note/relation/images/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按笔记 ID 读取图片映射及图片基础信息，返回给前端用于绑定状态展示、差异确认和详情页渲染。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListImageSimpleVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ImageSimpleVO|
|&emsp;&emsp;imageId||integer(int64)||
|&emsp;&emsp;noteId||integer(int64)||
|&emsp;&emsp;parsedImageName||string||
|&emsp;&emsp;filename||string||
|&emsp;&emsp;ossUrl||string||
|&emsp;&emsp;isPublic||integer(int32)||
|&emsp;&emsp;isPass||integer(int32)||
|&emsp;&emsp;isCrossUser||integer(int32)||
|&emsp;&emsp;isMissing||integer(int32)||
|&emsp;&emsp;createTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
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
	]
}
```


## 查询反向引用笔记


**接口地址**:`/user/note/relation/backlinks/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按笔记 ID 反查所有引用了它的源笔记。仅返回当前用户拥有，或源笔记已公开(status=6)的引用方；目标笔记本身需为当前用户拥有或已公开。</p>



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


## 查询标签反向引用笔记


**接口地址**:`/user/note/relation/backlinks/tag/{tagId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按标签 ID 反查所有引用了它的源笔记。目标标签需为当前用户拥有或已通过审核(isPass=1)。</p>



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


## 查询图片反向引用笔记


**接口地址**:`/user/note/relation/backlinks/image/{imageId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按图片 ID 反查所有引用了它的源笔记。目标图片需为当前用户拥有或已公开(isPublic=1)。</p>



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


## 解绑标签映射


**接口地址**:`/user/note/relation/tag/unbind/{mappingId}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>解除指定标签映射行与标签的绑定关系，清空 tagId 与审核标记后重新计算笔记缺失信息。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|mappingId|映射行ID|path|true|integer(int64)||


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


## 解绑图片映射


**接口地址**:`/user/note/relation/image/unbind/{mappingId}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>解除指定图片映射行的图片绑定，清空 imageId 和跨用户标记，并重新判断笔记是否仍存在缺失信息。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|mappingId|映射行ID|path|true|integer(int64)||


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


## 解绑双链笔记映射


**接口地址**:`/user/note/relation/each/unbind/{mappingId}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>解除指定双链映射行与目标笔记的绑定关系，清空 targetNoteId 后重算笔记关联完整性。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|mappingId|映射行ID|path|true|integer(int64)||


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


# User-标签管理


## 发起标签审核申请


**接口地址**:`/user/tag/submitAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>传入标签 ID，发起对该标签的审核申请。仅允许申请审核自己的标签，且该标签不能已通过审核或已有待审核申请。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|标签ID|query|true|integer(int64)||


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


## 解除标签绑定


**接口地址**:`/user/tag/remove`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>解除标签与笔记之间的绑定关系。仅允许操作自己的标签和资源。</p>



**请求示例**:


```javascript
{
  "tagId": 1,
  "targetId": 1
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userTagRemoveDTO|标签解绑请求（标签ID、目标资源ID、目标资源类型）|body|true|UserTagRemoveDTO|UserTagRemoveDTO|
|&emsp;&emsp;tagId|标签ID||true|integer(int64)||
|&emsp;&emsp;targetId|目标资源ID（笔记ID）||true|integer(int64)||


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


## 条件查询标签列表


**接口地址**:`/user/tag/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。</p>



**请求示例**:


```javascript
{
  "keyword": "",
  "scope": "",
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userTagQueryDTO|标签查询条件（关键词、分页参数）|body|true|UserTagQueryDTO|UserTagQueryDTO|
|&emsp;&emsp;keyword|||false|string||
|&emsp;&emsp;scope|||false|string||
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


## 撤销标签审核申请


**接口地址**:`/user/tag/cancelAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>撤销当前用户的标签审核申请，仅删除待审核记录。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|标签ID|query|true|integer(int64)||


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


## 批量新增标签


**接口地址**:`/user/tag/batch-add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量创建标签时先去重并过滤空值，再对比当前用户已有标签列表；仅插入不存在的标签，返回成功数量和已存在标签列表。</p>



**请求示例**:


```javascript
{
  "tagNames": []
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|tagBatchAddDTO|批量新增标签请求（标签名称列表）|body|true|TagBatchAddDTO|TagBatchAddDTO|
|&emsp;&emsp;tagNames|||true|array|string|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultTagBatchAddVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||TagBatchAddVO|TagBatchAddVO|
|&emsp;&emsp;successCount||integer(int32)||
|&emsp;&emsp;existingTags||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"successCount": 0,
		"existingTags": []
	}
}
```


## 绑定标签到资源


**接口地址**:`/user/tag/assign`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>将标签绑定到笔记或主题。绑定前会校验标签归属和目标资源的存在性。</p>



**请求示例**:


```javascript
{
  "tagId": 1,
  "targetId": 1
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userTagAssignDTO|标签绑定请求（标签ID、目标资源ID、目标资源类型）|body|true|UserTagAssignDTO|UserTagAssignDTO|
|&emsp;&emsp;tagId|标签ID||true|integer(int64)||
|&emsp;&emsp;targetId|目标资源ID（笔记ID）||true|integer(int64)||


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


## 新增标签


**接口地址**:`/user/tag/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>从当前登录用户上下文获取 userId 后创建单个标签；服务层会先清洗名称、校验长度，再检查同名标签是否已存在，避免重复创建。</p>



**请求示例**:


```javascript
{
  "tagName": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|tagAddDTO|新增标签请求（标签名称）|body|true|TagAddDTO|TagAddDTO|
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


## 查询当前用户标签列表


**接口地址**:`/user/tag`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>查询当前登录用户创建的所有标签，返回标签的基本信息（ID、名称、创建时间）。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListUserTagSimpleVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|UserTagSimpleVO|
|&emsp;&emsp;id|标签ID|integer(int64)||
|&emsp;&emsp;tagName|标签名称|string||
|&emsp;&emsp;createTime|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 1,
			"tagName": "Java",
			"createTime": ""
		}
	]
}
```


## 获取用户标签统计


**接口地址**:`/user/tag/stats`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回当前用户的标签总数和已通过审核数。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultTagStatsVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||TagStatsVO|TagStatsVO|
|&emsp;&emsp;tagCount|Total tag count of current user|integer(int64)||
|&emsp;&emsp;passedCount|Passed tag count of current user|integer(int64)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"tagCount": 0,
		"passedCount": 0
	}
}
```


## 批量删除标签


**接口地址**:`/user/tag/delete`


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


# User-笔记管理


## 修改笔记元信息


**接口地址**:`/user/note/{id}/info`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


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
|id|笔记ID|path|true|integer(int64)||
|noteModifyInfoDTO|笔记元信息修改请求（描述、主题ID等）|body|true|NoteModifyInfoDTO|NoteModifyInfoDTO|
|&emsp;&emsp;id|笔记ID||true|integer(int64)||
|&emsp;&emsp;topicId|主题ID||false|integer(int64)||
|&emsp;&emsp;description|笔记描述||false|string||


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


## 修改笔记源文件


**接口地址**:`/user/note/upload/{noteId}`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>校验笔记归属后读取旧 Markdown 内容，与新文件一起重新扫描标签、图片和双链引用并计算 Diff；新内容仅写入临时版本和变更记录，等待后续确认或回滚。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||
|file|新笔记文件|query|true|file||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteDiffVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteDiffVO|NoteDiffVO|
|&emsp;&emsp;oldTags||array|string|
|&emsp;&emsp;newTags||array|string|
|&emsp;&emsp;oldImages||array|string|
|&emsp;&emsp;newImages||array|string|
|&emsp;&emsp;oldNoteReflection||array|string|
|&emsp;&emsp;newNoteReflection||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"oldTags": [],
		"newTags": [],
		"oldImages": [],
		"newImages": [],
		"oldNoteReflection": [],
		"newNoteReflection": []
	}
}
```


## 设置笔记发布状态


**接口地址**:`/user/note/publish/{noteId}/{status}`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据 status 设置笔记发布或下架；发布时必须已存在转换缓存，并且标签、图片、双链三类关联都已通过审核，以及笔记本身通过审核，否则拒绝发布。用户端调用的时候会校验笔记是否具有所属权！</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||
|status|发布状态（1:发布, 0:下架）|path|true|integer(int32)||


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


## 上传笔记


**接口地址**:`/user/note/upload`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>从当前登录用户上下文获取 userId 后上传 Markdown 文件，先校验主题是否存在与同主题同名唯一性，再一次性扫描标签、图片和双链引用并建立映射；同时落库笔记原文、初始化缺失状态，最终返回 noteId 与缺失图片列表。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|file|笔记Markdown文件|query|true|file||
|topicId|所属主题ID（可选）|query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteUploadVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteUploadVO|NoteUploadVO|
|&emsp;&emsp;noteId||integer(int64)||
|&emsp;&emsp;missingTags||array|string|
|&emsp;&emsp;missingImages||array|string|
|&emsp;&emsp;missingNoteNames||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"noteId": 0,
		"missingTags": [],
		"missingImages": [],
		"missingNoteNames": []
	}
}
```


## 确认或取消笔记变更


**接口地址**:`/user/note/upload/{noteId}/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>对 modify-upload 产生的待确认 Diff 进行最终处理：确认时用新内容覆盖旧内容并重建关联映射，取消时清理临时内容和变更记录；整个过程保持笔记原有发布状态不变。</p>



**请求示例**:


```javascript
{
  "id": 0,
  "confirm": true
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||
|noteChangeConfirmDTO|变更确认请求（包含确认或取消标记）|body|true|NoteChangeConfirmDTO|NoteChangeConfirmDTO|
|&emsp;&emsp;id|||false|integer(int64)||
|&emsp;&emsp;confirm|||true|boolean||


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


## 发起笔记审核申请


**接口地址**:`/user/note/submitAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|笔记ID|query|true|integer(int64)||


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


## 条件查询笔记列表


**接口地址**:`/user/note/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "topicId": 0,
  "title": "",
  "scope": "",
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userNoteQueryDTO|笔记查询条件（主题ID、状态、分页参数）|body|true|UserNoteQueryDTO|UserNoteQueryDTO|
|&emsp;&emsp;topicId|||false|integer(int64)||
|&emsp;&emsp;title|||false|string||
|&emsp;&emsp;scope|||false|string||
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


## 转换笔记


**接口地址**:`/user/note/convert`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>将笔记的 Markdown 源文件转换成 HTML 文件，并保存到数据库中。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|query|true|integer(int64)||


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


**接口地址**:`/user/note/convert`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>删除笔记转换缓存，并清理临时文件和转换记录；同时将笔记状态转换为“待转换”。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|query|true|integer(int64)||


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


## 撤销笔记审核申请


**接口地址**:`/user/note/cancelAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>撤销当前用户的笔记审核申请。笔记状态从待审核回退到已转换，同时删除待审核记录。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|笔记ID|query|true|integer(int64)||


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


## 查询当前用户笔记列表


**接口地址**:`/user/note`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|dto|笔记搜索/查询请求|query|true|UserNoteSearchDTO|UserNoteSearchDTO|
|&emsp;&emsp;keyword|搜索关键词（支持标题模糊搜索）||false|string||
|&emsp;&emsp;topicId|主题ID||false|integer(int64)||
|&emsp;&emsp;tagId|标签ID||false|integer(int64)||
|&emsp;&emsp;scope|查询范围：global=全局，其他值=仅当前用户||false|string||
|&emsp;&emsp;pageNum|页码（默认1）||false|integer(int32)||
|&emsp;&emsp;pageSize|每页大小（默认10）||false|integer(int32)||
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


## 查看笔记详情


**接口地址**:`/user/note/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


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


## 查询变更 Diff 详情


**接口地址**:`/user/note/upload/{noteId}/diff`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>读取指定笔记的旧内容、新内容和 diff 记录，返回给前端用于变更确认页面展示；若没有待确认内容则按业务规则返回不存在。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteModifyDiffDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteModifyDiffDetailVO|NoteModifyDiffDetailVO|
|&emsp;&emsp;noteId||integer(int64)||
|&emsp;&emsp;oldSource||string||
|&emsp;&emsp;newSource||string||
|&emsp;&emsp;diff||NoteChangeDiffVO|NoteChangeDiffVO|
|&emsp;&emsp;&emsp;&emsp;noteId||integer||
|&emsp;&emsp;&emsp;&emsp;status||integer||
|&emsp;&emsp;&emsp;&emsp;oldFileSize||integer||
|&emsp;&emsp;&emsp;&emsp;newFileSize||integer||
|&emsp;&emsp;&emsp;&emsp;diffFileSize||integer||
|&emsp;&emsp;&emsp;&emsp;diff||NoteDiffVO|NoteDiffVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;oldTags||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;newTags||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;oldImages||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;newImages||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;oldNoteReflection||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;newNoteReflection||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"noteId": 0,
		"oldSource": "",
		"newSource": "",
		"diff": {
			"noteId": 0,
			"status": 0,
			"oldFileSize": 0,
			"newFileSize": 0,
			"diffFileSize": 0,
			"diff": {
				"oldTags": [],
				"newTags": [],
				"oldImages": [],
				"newImages": [],
				"oldNoteReflection": [],
				"newNoteReflection": []
			}
		}
	}
}
```


## 获取笔记Markdown源内容


**接口地址**:`/user/note/source/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|笔记ID|path|true|integer(int64)||


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


## 全文搜索笔记


**接口地址**:`/user/note/search`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|dto|笔记搜索/查询请求|query|true|UserNoteSearchDTO|UserNoteSearchDTO|
|&emsp;&emsp;keyword|搜索关键词（支持标题模糊搜索）||false|string||
|&emsp;&emsp;topicId|主题ID||false|integer(int64)||
|&emsp;&emsp;tagId|标签ID||false|integer(int64)||
|&emsp;&emsp;scope|查询范围：global=全局，其他值=仅当前用户||false|string||
|&emsp;&emsp;pageNum|页码（默认1）||false|integer(int32)||
|&emsp;&emsp;pageSize|每页大小（默认10）||false|integer(int32)||
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


## 获取用户笔记统计


**接口地址**:`/user/note/overview`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultNoteStatsVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||NoteStatsVO|NoteStatsVO|
|&emsp;&emsp;noteTotalCount|Total note count of current user (not deleted)|integer(int64)||
|&emsp;&emsp;publicNoteCount|Published note count of current user|integer(int64)||
|&emsp;&emsp;passedNoteCount|Passed note count of current user|integer(int64)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"noteTotalCount": 0,
		"publicNoteCount": 0,
		"passedNoteCount": 0
	}
}
```


## 获取笔记转换后的HTML内容


**接口地址**:`/user/note/converted/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>获取笔记转换后的 HTML 内容，但是一般用于获取自己的笔记形式，不能用来查询公开的笔记内容。</p>



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


## 删除笔记


**接口地址**:`/user/note/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|笔记ID|path|true|integer(int64)||


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


# User-图片管理


## 修改图片元信息


**接口地址**:`/user/image/modify-info`


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


## 替换图片源文件


**接口地址**:`/user/image/modify-file`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>校验图片归属与存储类型后，覆盖上传新的图片文件并更新 ossUrl 和 fileSize；当前实现仅支持已接入的云存储类型。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|图片ID|query|true|integer(int64)||
|file|新文件|query|true|file||


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


## 上传图片


**接口地址**:`/user/image/upload`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>从当前登录用户上下文获取 userId 后，将图片上传到默认对象存储并创建图片记录；上传前会先经过文件大小、后缀和存储配额校验，成功后返回可访问地址。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|file|图片文件|query|true|file||
|topicId|所属主题ID（可选）|query|false|integer(int64)||


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


## 发起图片审核申请


**接口地址**:`/user/image/submitAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>传入图片 ID，发起对该图片的审核申请。仅允许申请审核自己的图片，且该图片不能已通过审核或已有待审核申请。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|图片ID|query|true|integer(int64)||


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


## 条件查询图片列表


**接口地址**:`/user/image/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。</p>



**请求示例**:


```javascript
{
  "topicId": 0,
  "filename": "",
  "scope": "",
  "pageNum": 0,
  "pageSize": 0,
  "pageSizeOrDefault": 0,
  "pageNumOrDefault": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userImageQueryDTO|用户图片查询条件（主题ID、文件名、分页参数）|body|true|UserImageQueryDTO|UserImageQueryDTO|
|&emsp;&emsp;topicId|||false|integer(int64)||
|&emsp;&emsp;filename|||false|string||
|&emsp;&emsp;scope|||false|string||
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


## 撤销图片审核申请


**接口地址**:`/user/image/cancelAudit`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>撤销当前用户的图片审核申请，仅删除待审核记录。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|图片ID|query|true|integer(int64)||


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


## 获取图片详情


**接口地址**:`/user/image/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据图片ID查询图片记录，返回图片访问URL、文件名、大小、上传时间等元数据信息。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|图片ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultUserImageDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||UserImageDetailVO|UserImageDetailVO|
|&emsp;&emsp;id|图片ID|integer(int64)||
|&emsp;&emsp;filename|文件名|string||
|&emsp;&emsp;ossUrl|图片访问URL|string||
|&emsp;&emsp;fileSize|文件大小（字节）|integer(int64)||
|&emsp;&emsp;uploadTime|上传时间|string(date-time)||
|&emsp;&emsp;isPublic|是否公开：0-否，1-是|integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 1,
		"filename": "example.png",
		"ossUrl": "https://oss.example.com/image/1/xxx.png",
		"fileSize": 102400,
		"uploadTime": "",
		"isPublic": 1
	}
}
```


## 删除图片


**接口地址**:`/user/image/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据图片ID删除图片。从云存储删除对应对象文件，并从数据库删除图片记录。仅允许删除自己的图片。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|图片ID|path|true|integer(int64)||


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


## 获取用户图片统计


**接口地址**:`/user/image/overview`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回当前用户的图片总数和已通过审核数。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultImageOverviewVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||ImageOverviewVO|ImageOverviewVO|
|&emsp;&emsp;imageCount|Total image count of current user|integer(int64)||
|&emsp;&emsp;passedCount|Passed image count of current user|integer(int64)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"imageCount": 0,
		"passedCount": 0
	}
}
```


# User-用户认证


## 获取当前用户信息


**接口地址**:`/user/user/me`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>从 JWT 中解析当前用户ID，查询并返回用户详情（不含密码等敏感字段）。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultUserDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||UserDetailVO|UserDetailVO|
|&emsp;&emsp;id|用户ID|integer(int64)||
|&emsp;&emsp;username|登录用户名|string||
|&emsp;&emsp;nickname|用户昵称|string||
|&emsp;&emsp;email|邮箱地址|string||
|&emsp;&emsp;roleId|角色ID：1-创建者，2-管理员，3-普通用户，4-VIP用户|integer(int64)||
|&emsp;&emsp;status|账号状态：2-未激活，1-正常，0-禁用，-1-已删除|integer(int32)||
|&emsp;&emsp;maxStorageBytes|用户个性化最大存储空间(字节)|integer(int64)||
|&emsp;&emsp;usedStorageBytes|用户当前已用存储空间(字节)|integer(int64)||
|&emsp;&emsp;createTime|注册时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"username": "",
		"nickname": "",
		"email": "",
		"roleId": 0,
		"status": 0,
		"maxStorageBytes": 0,
		"usedStorageBytes": 0,
		"createTime": ""
	}
}
```


## 更新当前用户信息


**接口地址**:`/user/user/me`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>仅允许修改当前登录用户自身的昵称、邮箱等资料字段；修改密码可以复用这个接口</p>



**请求示例**:


```javascript
{
  "nickname": "",
  "email": "",
  "password": "",
  "newPassword": "",
  "confirmPassword": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userProfileUpdateDTO|用户资料更新请求（昵称、邮箱等可修改字段）|body|true|UserProfileUpdateDTO|UserProfileUpdateDTO|
|&emsp;&emsp;nickname|||false|string||
|&emsp;&emsp;email|||false|string||
|&emsp;&emsp;password|||false|string||
|&emsp;&emsp;newPassword|||false|string||
|&emsp;&emsp;confirmPassword|||false|string||


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


## 删除账户（软删除）


**接口地址**:`/user/user/me`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>将当前登录用户账户状态更新为软删除状态，保留历史数据，避免物理删除造成关联记录丢失。</p>



**请求参数**:


暂无


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


## 匿名重发激活邮件


**接口地址**:`/user/user/resend-activation`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>未激活用户可通过用户名或邮箱重新发送激活邮件，无需登录</p>



**请求参数**:


暂无


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


## 用户注册


**接口地址**:`/user/user/register`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>创建普通用户账号并发送邮箱激活邮件；账号激活前无法登录使用。</p>



**请求示例**:


```javascript
{
  "username": "",
  "password": "",
  "confirmPassword": "",
  "email": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|userRegisterDTO|用户注册请求，包含用户名、密码和基本信息|body|true|UserRegisterDTO|UserRegisterDTO|
|&emsp;&emsp;username|||true|string||
|&emsp;&emsp;password|||true|string||
|&emsp;&emsp;confirmPassword|||true|string||
|&emsp;&emsp;email|||true|string||


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


**接口地址**:`/user/user/logout`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>用户退出；删除 Redis 中的 JWT 令牌。</p>



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


## 用户登录


**接口地址**:`/user/user/login`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>先校验用户名和密码是否匹配，登录成功后将用户 ID 写入 JWT claims 并签发令牌返回；后续接口会通过该 token 解析当前用户。</p>



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
|userLoginDTO|用户登录请求，包含用户名和密码|body|true|UserLoginDTO|UserLoginDTO|
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


## 通过激活码激活账号


**接口地址**:`/user/user/active-code`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>通过邮箱中收到的 6 位数字激活码完成账号激活，无需 JWT 令牌</p>



**请求参数**:


暂无


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


## 获取用户概览


**接口地址**:`/user/user/overview`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回当前用户的基本信息，不包含资源统计数据。</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultUserOverviewVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||UserOverviewVO|UserOverviewVO|
|&emsp;&emsp;username|Login username|string||
|&emsp;&emsp;nickname|Nickname|string||
|&emsp;&emsp;email|Email|string||
|&emsp;&emsp;roleId|Role id: 1-creator, 2-admin, 3-user, 4-vip|integer(int64)||
|&emsp;&emsp;status|Status: 2-unactivated, 1-active, 0-disabled, -1-deleted|integer(int32)||
|&emsp;&emsp;maxStorageBytes|Maximum storage bytes|integer(int64)||
|&emsp;&emsp;usedStorageBytes|Used storage bytes|integer(int64)||
|&emsp;&emsp;createTime|Register time|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"username": "",
		"nickname": "",
		"email": "",
		"roleId": 0,
		"status": 0,
		"maxStorageBytes": 0,
		"usedStorageBytes": 0,
		"createTime": ""
	}
}
```


## 用户激活


**接口地址**:`/user/user/active/{token}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>用户注册成功后，会通过邮件发送激活链接，点击链接后调用该接口完成用户激活。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|token||path|true|string||


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


# User-音频生成


## 分页查询当前用户音频任务列表


**接口地址**:`/user/audio/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


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


## 提交音频生成任务


**接口地址**:`/user/audio/generate`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>提交文本转语音任务，指定语速、背景音类型和噪音因子，任务异步处理，返回 taskId 供后续轮询。</p>



**请求示例**:


```javascript
{
  "text": "",
  "speed": 0,
  "noiseType": "",
  "noiseFactor": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|audioTaskSubmitDTO|音频生成任务提交请求|body|true|AudioTaskSubmitDTO|AudioTaskSubmitDTO|
|&emsp;&emsp;text|待生成的纯文本内容||true|string||
|&emsp;&emsp;speed|播放倍速 (0.5 ~ 3.0)||true|number||
|&emsp;&emsp;noiseType|背景音标识符 (PURE/WHITE_NOISE/PINK_NOISE/BROWN_NOISE/CAFE/AIRPORT/SUBWAY)||true|string||
|&emsp;&emsp;noiseFactor|背景音量因子 (0.0 ~ 2.0)，默认 0.5||false|number||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAudioTaskSubmitVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AudioTaskSubmitVO|AudioTaskSubmitVO|
|&emsp;&emsp;taskId|任务ID|integer(int64)||
|&emsp;&emsp;status|任务状态：0=排队中|integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"taskId": 0,
		"status": 0
	}
}
```


## 查询音频任务状态


**接口地址**:`/user/audio/status/{taskId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据 taskId 查询任务当前状态与结果链接，仅能查询当前用户自己的任务。</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|taskId|任务ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAudioTaskVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AudioTaskVO|AudioTaskVO|
|&emsp;&emsp;id|任务ID|integer(int64)||
|&emsp;&emsp;sourceText|音频源文本|string||
|&emsp;&emsp;userId|提交任务的用户ID|integer(int64)||
|&emsp;&emsp;speed|语速倍率|number||
|&emsp;&emsp;noiseType|背景音类型|string||
|&emsp;&emsp;noiseFactor|背景音量因子|number||
|&emsp;&emsp;status|任务状态：0=排队中, 1=合成中, 2=已完成, -1=失败|integer(int32)||
|&emsp;&emsp;resultUrl|成功后音频下载链接|string||
|&emsp;&emsp;errorMsg|失败时的错误信息|string||
|&emsp;&emsp;createTime|任务创建时间|string(date-time)||
|&emsp;&emsp;completedDate|任务完成日期|string(date)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"sourceText": "",
		"userId": 0,
		"speed": 0,
		"noiseType": "",
		"noiseFactor": 0,
		"status": 0,
		"resultUrl": "",
		"errorMsg": "",
		"createTime": "",
		"completedDate": ""
	}
}
```