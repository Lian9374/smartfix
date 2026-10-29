# C：只关闭微服务实验版，保留原版

只关闭19111–19114所属的stage9应用，保留数据库、卷、附件与所有备份。先读取`.migration-runtime/stage9/resources-private.json`；脚本检查明确的容器ID、挂载和进程产物路径，不要手动结束所有Java/Python进程。

在`C:\Users\zhour\smartfix-microservices`执行，每条仅在该服务登记为运行时执行：

```powershell
python tools/migration/stage9.py stop core
python tools/migration/stage9.py stop lb
python tools/migration/stage9.py stop a
python tools/migration/stage9.py stop b
```

若以后明确启动了c实例19115，还需按同一清单核对后执行`stop c`；当前c未运行。不要运行`docker compose down -v`、`docker system prune`、模糊容器批量停止或删除命令。

随后打开[19090原版恢复登录](http://127.0.0.1:19090/login)，确认可登录；原系统若原本在运行，仍按原入口使用。保持`smartfix-db`、`sf-stage1-20261028-105721-db`、15443和19090不动。19121是额外恢复验收副本，也无需关闭。

本阶段将实际执行上述四应用关闭，并在全部关闭时检查19090健康和保护容器未变，再恢复实验版供验收；结果见EVIDENCE.json。这是停止实验的演练，不是把新数据回迁到原系统。

如果仅为暂停实验，数据库15483容器可保留运行，避免丢失任何数据；本卡不要求停止或删除数据库。

重新使用实验版按B卡逐个启动a、b、lb、core。实验版新增数据仍保留在原实验逻辑库与附件目录；禁止用旧备份覆盖当前实验库。需要将新增数据恢复为单体时，参考阶段7已验证的暂停写入、排空、归档、恢复到新库、增量导入、字段与序列校验流程；阶段7脚本固定其自有资源，不能直接对stage9或原库运行。真实回迁应另行编写并验收对应资源身份，不能直接切回过期数据。
