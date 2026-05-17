# API 参考

所有路径前缀 `/api`（前端 Nginx 代理自动添加和移除）。

## 认证: `/auth`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/auth/sendCode` | 发送验证码 | 无 |
| POST | `/auth/register` | 验证码注册 | 无 |
| POST | `/auth/loginByCode` | 验证码登录 | 无 |
| POST | `/auth/resetPassword` | 重置密码 | 无 |

## 用户: `/sysUser`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/sysUser/login` | 用户名+密码登录 | 无 |
| POST | `/sysUser/update` | 更新个人信息 | Token |
| POST | `/sysUser/password` | 修改密码 | Token |
| GET | `/sysUser/me` | 当前用户信息 | Token |
| GET | `/sysUser/profile/{id}` | 用户公开资料 | 无 |
| GET | `/sysUser/list` | 用户列表 | Admin |
| POST | `/sysUser/adminUpdate` | 管理员修改用户 | Admin |
| POST | `/sysUser/ban/{id}` | 封禁用户 | Admin |
| DELETE | `/sysUser/{id}` | 删除用户 | Admin |

## 书籍: `/sysBook`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/sysBook/list` | 书籍列表（分页+筛选） | 无 |
| GET | `/sysBook/{id}` | 书籍详情 | 按权限 |
| GET | `/sysBook/catalog/{bookId}` | 章节目录 | 按权限 |
| GET | `/sysBook/chapter/{chapterId}` | 章节内容 | 按权限 |
| GET | `/sysBook/hot` | 热门书籍 | 无 |
| GET | `/sysBook/rank` | 排行榜 | 无 |
| GET | `/sysBook/recommend` | AI 推荐 | Token |
| POST | `/sysBook/upload` | 上传书籍文件 | Token |
| POST | `/sysBook/add` | 添加书籍 | Token |
| POST | `/sysBook/userUpload` | 用户上传书籍 | Token |
| POST | `/sysBook/analyze/{bookId}` | 解析章节 | Token |
| PUT | `/sysBook/update` | 管理员更新 | Admin |
| PUT | `/sysBook/userEdit` | 用户编辑 | 上传者 |
| DELETE | `/sysBook/{id}` | 删除 | Admin |
| POST | `/sysBook/applyPublic/{id}` | 申请公开 | Token |
| POST | `/sysBook/applyEdit` | 申请编辑 | Token |
| POST | `/sysBook/applyDelist/{id}` | 申请下架 | Token |
| GET | `/sysBook/myUploads/{userId}` | 我的上传 | Token |
| GET | `/sysBook/reviewRequests/pending` | 待审核列表 | Admin |
| POST | `/sysBook/reviewRequest/{id}` | 审核 | Admin |

## 搜索: `/search`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/search` | 全文搜索 | 无 |
| POST | `/search/sync` | 同步 MySQL→ES | Admin |
| POST | `/search/syncKb` | 同步到 Dify KB | Admin |

## 书架: `/bookshelf`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/bookshelf/add` | 加入书架 | Token |
| GET | `/bookshelf/list/{userId}` | 书架列表 | Token |
| GET | `/bookshelf/detail` | 书架书籍详情 | Token |
| POST | `/bookshelf/updateProgress` | 更新阅读进度 | Token |
| DELETE | `/bookshelf/remove/{id}` | 移出书架 | Token |
| DELETE | `/bookshelf/removeByBook` | 按书移除 | Token |

## 评论: `/comment`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/comment/list/{bookId}` | 书籍评论列表 | 无 |
| POST | `/comment/add` | 添加评论 | Token |
| POST | `/comment/like` | 点赞 | Token |
| DELETE | `/comment/{id}` | 删除评论 | 作者/Admin |
| PUT | `/comment/update` | 编辑评论 | 作者 |
| GET | `/comment/user/{userId}` | 用户评论 | 无 |

## 段落评论: `/paragraphComment`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/paragraphComment/list/{bookId}/{chapterIndex}/{paragraphIndex}` | 段落评论 | 无 |
| POST | `/paragraphComment/add` | 添加段落评论 | Token |
| POST | `/paragraphComment/like` | 点赞 | Token |
| PUT | `/paragraphComment/update` | 编辑 | 作者 |
| DELETE | `/paragraphComment/{id}` | 删除 | 作者/Admin |
| GET | `/paragraphComment/my/{bookId}/{userId}` | 我的评论 | Token |

## AI: `/difyreading`, `/ai`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/difyreading/analyze` | AI 分析（SSE 流式） | Token |
| POST | `/ai/tts` | TTS 合成 | Token |
| POST | `/ai/audio/generate` | 生成音频 | Token |

## 社交
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| **好友: `/friend`** |
| POST | `/friend/request` | 发送申请 | Token |
| POST | `/friend/accept/{id}` | 接受申请 | Token |
| POST | `/friend/reject/{id}` | 拒绝申请 | Token |
| GET | `/friend/list/{userId}` | 好友列表 | Token |
| GET | `/friend/pending/{userId}` | 待处理申请 | Token |
| GET | `/friend/search` | 搜索用户 | Token |
| DELETE | `/friend/{id}` | 删除好友 | Token |
| **聊天: `/chat`** |
| POST | `/chat/send` | 发送消息 | Token |
| GET | `/chat/history` | 聊天记录 | Token |
| GET | `/chat/unread/{userId}` | 未读消息数 | Token |
| POST | `/chat/read` | 标记已读 | Token |
| GET | `/chat/conversations/{userId}` | 会话列表 | Token |
| **分享: `/bookShare`, `/paragraphShare`, `/audioShare`** |
| POST | `/bookShare/send` | 分享书籍 | Token |
| GET | `/bookShare/received/{userId}` | 收到的分享 | Token |
| POST | `/bookShare/read/{id}` | 标记已读 | Token |
| DELETE | `/bookShare/{id}` | 删除 | Token |

## 书单: `/booklist`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/booklist/create` | 创建书单 | Token |
| GET | `/booklist/list/{userId}` | 用户书单 | 无 |
| GET | `/booklist/detail/{id}` | 书单详情 | 无 |
| DELETE | `/booklist/delete/{id}` | 删除书单 | 作者 |
| POST | `/booklist/addBook` | 加入书单 | Token |
| DELETE | `/booklist/removeBook` | 移出书单 | Token |
| GET | `/booklist/share/{shareCode}` | 通过分享码获取 | 无 |
| POST | `/booklist/import/{shareCode}` | 导入书单 | Token |

## 笔记: `/sysNote`
| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/sysNote/add` | 添加笔记 | Token |
| GET | `/sysNote/list/{bookId}` | 笔记列表 | Token |
| DELETE | `/sysNote/{id}` | 删除笔记 | 作者 |

## 鉴权方式

请求头携带 `Authorization: Bearer <token>` 或 `X-User-Token: <token>`。

公开端点（无需 Token）：`/auth/**`, `/sysUser/login`, `/sysUser/register`, `/sysUser/profile/*`, `/sysBook/list`, `/sysBook/hot`, `/sysBook/rank`, `/sysBook/detail/*`, `/sysBook/catalog/*`, `/sysBook/chapter/*`, `/search/**`, `/files/**`, `/ws/**`, `/comment/list/*`, `/paragraphComment/list/*`, `/booklist/share/*`
