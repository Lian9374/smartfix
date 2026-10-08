# ADR-005：NUS 真实在线校园地图

- 日期：2026-10-08
- 状态：按用户本次要求采用，扩展 ADR-004 的目录版

## 范围与来源

用户明确要求真实 NUS 建筑、类似 Google Maps 的动态在线底图，以及无需点击即可看到的故障／维修概览。本次授权采用外部在线底图，覆盖模块指南和 D-11 第一版“不使用外部地图 SDK”的建议；保留已有目录、权限和报修入口。

使用本地固定版本 Leaflet 1.9.4（BSD-2-Clause，见 `docs/licenses/Leaflet-1.9.4.txt`），通过 HTTPS 加载新加坡土地管理局 OneMap Grey 地图瓦片。无需 API key，不引入 Google Maps 计费配置。保留 OneMap 标识、SLA 归属及开放数据许可链接。底图显示真实道路和建筑轮廓，可平移、缩放；不是实时卫星影像，也不提供人、车辆或室内定位。

建筑目录 `data/nus-campus-buildings.json` 来自 NUS 官方校园地图公开检索目录和 OneMap 官方公开地址检索，核对日期为 2026-10-08。保留 183 个有名字且有有效坐标的建筑／地标点，覆盖 Kent Ridge、UTown、Bukit Timah、Outram。采用建筑／住宅／明确独立建筑或地标条目，剔除部门办公室、联系方式、房间资料，以及仅标为“NATIONAL UNIVERSITY OF SINGAPORE”的无具体建筑名称地址点。合并明确同一地点的名字和旧代码，保留别名供检索和既有报修地点关联。优先使用 OneMap 当前建筑地址坐标。

这是可核实公开目录的快照，不是 NUS 的完整资产台账；不能承诺每个附属小建筑都有独立名称，也不能保证上游的旧学院名称已同步最新调整。真实底图的建筑轮廓与命名点目录分开：不以某个部门的坐标推断其所有房间或其他建筑。目录刷新须重新核对并评审，不在运行时抓取校园目录。

来源：

- [NUS 官方校园地图](https://map.nus.edu.sg/)，公开搜索接口 `/index.php/search/ajax_auto?qword=`。
- [OneMap 搜索 API](https://www.onemap.gov.sg/apidocs/search)：`/api/common/elastic/search?searchVal=NATIONAL%20UNIVERSITY%20OF%20SINGAPORE&returnGeom=Y&getAddrDetails=Y&pageNum=...`，此次核对 220 个地址结果，排除无明确名字的点及合并重复地址。
- [OneMap Grey 底图说明](https://www.onemap.gov.sg/docs/maps/grey.html)、[新加坡开放数据许可](https://www.onemap.gov.sg/legal/opendatalicence.html)。
- [Leaflet 1.9.4 发布](https://github.com/Leaflet/Leaflet/releases/tag/v1.9.4)。
- 学院更名由现行 NUS 页面核对：[Acacia 官网](https://acacia.nus.edu.sg/contact-us/)、[NUS 更名公告](https://news.nus.edu.sg/acacia-college/)、[NUS College 2026 住宿说明](https://nuscollege.nus.edu.sg/faqs/residential-living/)、[CAPT 原 Angsana 名称说明](https://blog.nus.edu.sg/reslife/2024/02/20/residential-colleges-shaping-citizens-of-tomorrow/)。Cinnamon → Acacia，Angsana → CAPT；旧 Yale-NUS 点标为 NUS College 西区，旧名保留为别名。旧目录位于 Cinnamon 的组织条目“NUS College”不再作为一个独立建筑；泛指学院的名字不足以确定某座具体建筑。

## 业务状态

地图叠加的是 SmartFix 的真实记录，不声称是 NUS 官方维护状态源。红点代表未完成的报修或 `OUT_OF_SERVICE` 设施；橙点代表 `IN_PROGRESS` 报修或 `UNDER_MAINTENANCE` 设施。`SUBMITTED`、`UNDER_REVIEW`、`ASSIGNED`、`REOPENED` 仍计入待处理故障；已分配任务不等于现场维修已经开始。`RESOLVED`、`CONFIRMED`、`CLOSED`、`REJECTED`、`CANCELLED` 不作为当前故障或维修。恢复／重新打开请求会在下一次更新反映。

一个地点同时有故障和维修时，两种文字计数都保留，点优先显示红色。概览数字是“受影响建筑数”；报修条数与人工维护的设施状态分别显示，不把可能有关联的两类记录加总成独立故障数量。灰色只表示其他建筑，不承诺整栋建筑正常；设施的 `OPERATIONAL` 状态仍在原目录查看。

按启用 Location 的 `building` 与公开名称／别名进行精确归一化匹配（忽略英文大小写和标点）。不通过房间或模糊搜索猜坐标；歧义别名不匹配。无法匹配的活动记录单列“未定位”数量，停用地点不会暴露名字。V22 仅新增通用建筑级 Location，保留旧房间、ID、报修和设施状态，遇到已有相同代码时不覆盖或重新启用。迁移校正落后的地点 identity 序列以兼容导入数据，不创建模拟设施或报修。

## 模块、更新与降级

facility 拥有目录和空间映射；request 提供按地点／状态的数据库聚合读接口；reporting 通过两个模块的公开服务组合响应，避免 facility→request→facility 循环依赖。地图控制器移动到 reporting。`GET /campus-map/status` 仅允许有效登录账户，服务再验证账户状态，`Cache-Control: no-store`。响应仅含公共建筑坐标、名称／别名及计数，不含 location/request/user ID、工单号、标题、描述、房间、联系方式或附件。

打开页面先渲染最新快照，再每 30 秒查询一次状态；后台标签暂停，返回时更新。超时／网络错误保留上次成功数据并显示旧数据时间；会话失效暂停更新并提示重新登录。地图瓦片失败不影响统计与目录。使用本地 JS/CSS，网络只用于 OneMap 瓦片／来源标识和同源状态接口。标记没有点击事件或详情弹窗，故障标签常驻；普通建筑细标签在放大后显示。没有 JavaScript 时，服务端建筑目录与状态仍可读。

## 验证

`CampusMapIntegrationIT` 额外覆盖真实坐标、四个校区、无数据不虚构状态、跨房间／别名合并、请求全程状态变更、重新打开、设施状态独立、无法定位的记录、状态接口权限与字段边界。`CampusCatalogueMigrationPostgresIT` 在专用测试数据库的随机 schema 验证 V21→V22 升级、已有房间／停用记录保留、目录与 SQL 一致、重跑迁移不重复和无模拟业务数据。浏览器验证在线瓦片、四区切换、建筑搜索、无需点击的标签、定时刷新、断网／会话失效、桌面和窄屏布局。
