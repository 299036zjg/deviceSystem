#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
设备维修保养系统 端到端冒烟测试（对应验收表 AT01 ~ AT12）

前置：后端已启动（java -jar target/sems.jar），数据库含演示数据
用法：python scripts/smoke_test.py [--base http://localhost:8080/api/v1]
输出：控制台 PASS/FAIL 与 scripts/test-report.md

仅使用 Python 标准库，不需要安装额外依赖。
"""

import argparse
import json
import os
import random
import sys
import time
import uuid
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

BASE = "http://localhost:8080/api/v1"
TOKEN = None
PASSED = 0
FAILED = 0
REPORT = []
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_DIR = os.path.join(SCRIPT_DIR, "test-images")
TODAY = datetime.now().strftime("%Y-%m-%d")
TOMORROW = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
STAMP = datetime.now().strftime("%H%M%S")


class Result:
    def __init__(self, status, obj=None, payload=b"", content_type=""):
        self.status = status
        self.obj = obj or {}
        self.payload = payload
        self.content_type = content_type

    @property
    def code(self):
        return self.obj.get("code", -1)

    @property
    def message(self):
        return self.obj.get("message", "")

    @property
    def data(self):
        return self.obj.get("data")


def http(method, path, body=None, token=None, file_field=None, raw=False):
    url = BASE + path
    headers = {}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if file_field:
        boundary = "----sems" + uuid.uuid4().hex
        chunks = []
        for name, value in (file_field.get("fields") or {}).items():
            chunks.append(
                ('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n'
                 % (boundary, name, value)).encode("utf-8"))
        chunks.append(
            ('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\n'
             'Content-Type: %s\r\n\r\n' % (boundary, file_field["filename"], file_field["content_type"]))
            .encode("utf-8"))
        chunks.append(file_field["content"])
        chunks.append(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
        data = b"".join(chunks)
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read()
            status = resp.status
            ctype = resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as err:
        payload = err.read()
        status = err.code
        ctype = err.headers.get("Content-Type", "")
    obj = None
    if not raw and payload and "json" in ctype:
        try:
            obj = json.loads(payload.decode("utf-8"))
        except ValueError:
            obj = None
    return Result(status, obj, payload, ctype)


def record(name, ok, detail=""):
    global PASSED, FAILED
    if ok:
        PASSED += 1
    else:
        FAILED += 1
    mark = "PASS" if ok else "FAIL"
    REPORT.append("| %s | %s | %s |" % (mark, name, str(detail).replace("|", "/")))
    print("[%s] %s %s" % (mark, name, detail))


def ok(name, fn):
    res = fn()
    if res.code != 0:
        record(name, False, "期望成功，实际 code=%s（%s）" % (res.code, res.message))
        raise AssertionError(name + " 失败：" + str(res.message))
    record(name, True, "成功")
    return res.data


def fails(name, fn, codes):
    res = fn()
    if res.code not in codes:
        record(name, False, "期望错误码 %s，实际 code=%s（%s）" % (codes, res.code, res.message))
        raise AssertionError(name + " 失败")
    record(name, True, "code=%s %s" % (res.code, res.message))
    return res


def login(account, password):
    res = http("POST", "/auth/login", {"account": account, "password": password})
    if res.code != 0:
        raise AssertionError("登录失败 %s：%s" % (account, res.message))
    return res.data["token"]


def q(value):
    return urllib.parse.quote(str(value))


def prepare_images():
    os.makedirs(IMAGE_DIR, exist_ok=True)
    fault = os.path.join(IMAGE_DIR, "fault.jpg")
    work = os.path.join(IMAGE_DIR, "work.jpg")
    if os.path.exists(fault) and os.path.exists(work):
        return
    try:
        from PIL import Image, ImageDraw
        for path, color, text in ((fault, (200, 60, 60), "FAULT"), (work, (60, 140, 200), "DONE")):
            img = Image.new("RGB", (640, 480), color)
            draw = ImageDraw.Draw(img)
            draw.rectangle([40, 40, 600, 440], outline=(255, 255, 255), width=6)
            draw.text((60, 60), text, fill=(255, 255, 255))
            img.save(path, quality=85)
    except ImportError:
        # 没有 Pillow 时写入最小合法 PNG
        import base64
        tiny = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwHJ"
            "AeWkV0kAAAAASUVORK5CYII=")
        for path in (fault, work):
            with open(path, "wb") as fh:
                fh.write(tiny)
    with open(os.path.join(IMAGE_DIR, "not-image.txt"), "w", encoding="utf-8") as fh:
        fh.write("not an image")
    with open(os.path.join(IMAGE_DIR, "too-big.jpg"), "wb") as fh:
        fh.write(b"\xff\xd8" + bytes(random.getrandbits(8) for _ in range(6 * 1024 * 1024)))


def upload(name, filename, content_type, biz_type, token, sort_no=None):
    path = os.path.join(IMAGE_DIR, filename)
    with open(path, "rb") as fh:
        content = fh.read()
    fields = {"bizType": biz_type}
    if sort_no is not None:
        fields["sortNo"] = str(sort_no)
    return http("POST", "/files", token=token,
                file_field={"filename": filename, "content_type": content_type,
                            "content": content, "fields": fields})


def main():
    global BASE, TOKEN
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=BASE)
    parser.add_argument("--report", default=os.path.join(SCRIPT_DIR, "test-report.md"))
    args = parser.parse_args()
    BASE = args.base

    prepare_images()
    print("=" * 70)
    print("设备维修保养系统 冒烟测试，接口：%s" % BASE)
    print("=" * 70)

    # ----------------------------------------------------------------- AT01
    print("\n--- AT01 账号与会话 ---")
    admin = login("admin", "admin123")
    record("管理员登录成功", True, "admin")
    rep1 = login("rep1", "rep12345")
    rep2 = login("rep2", "rep12345")
    tech1 = login("tech1", "tech123")
    tech2 = login("tech2", "tech123")
    tech3 = login("tech3", "tech123")
    record("其他角色登录成功", True, "rep1 / rep2 / tech1 / tech2 / tech3")

    fails("错误密码被拒绝",
          lambda: http("POST", "/auth/login", {"account": "admin", "password": "wrong1234"}), (40101,))
    fails("不存在的账号统一提示",
          lambda: http("POST", "/auth/login", {"account": "nobody", "password": "wrong1234"}), (40101,))
    fails("无令牌访问被拒绝", lambda: http("GET", "/dashboard"), (40101,))

    fails("报修人访问账号管理被拒绝", lambda: http("GET", "/users", token=rep1), (40301,))
    fails("报修人访问工单列表被拒绝", lambda: http("GET", "/work-orders", token=rep1), (40301,))

    account = "rep_t" + STAMP
    created = ok("管理员新增账号", lambda: http("POST", "/users", {
        "account": account, "name": "测试报修人", "department": "生产一部",
        "role": "REPORTER", "password": "test12345"}, token=admin))
    fails("重复账号被拒绝", lambda: http("POST", "/users", {
        "account": account, "name": "测试报修人", "department": "生产一部",
        "role": "REPORTER", "password": "test12345"}, token=admin), (40001,))
    fails("初始密码不合规被拒绝", lambda: http("POST", "/users", {
        "account": "x" + STAMP, "name": "测试", "department": "生产一部",
        "role": "REPORTER", "password": "abcdefgh"}, token=admin), (40001,))
    new_token = login(account, "test12345")
    record("新账号可登录", True, account)
    ok("管理员重置密码", lambda: http("POST", "/users/%s/reset-password" % created["userId"],
                                 {"password": "reset12345"}, token=admin))
    fails("重置后旧令牌失效", lambda: http("GET", "/auth/profile", token=new_token), (40101,))
    new_token = login(account, "reset12345")
    record("重置后的新密码可登录", True, account)
    ok("停用账号", lambda: http("POST", "/users/%s/disable" % created["userId"], token=admin))
    fails("停用后令牌立即失效", lambda: http("GET", "/auth/profile", token=new_token), (40101,))
    fails("停用账号不能登录",
          lambda: http("POST", "/auth/login", {"account": account, "password": "reset12345"}), (40101,))
    ok("重新启用账号", lambda: http("POST", "/users/%s/enable" % created["userId"], token=admin))
    fails("最后一个启用的管理员不可停用",
          lambda: http("POST", "/users/1/disable", token=admin), (40901,))

    # ----------------------------------------------------------------- AT02
    print("\n--- AT02 设备台账 ---")
    eq_code = "TEST" + STAMP
    equipment = ok("新增设备", lambda: http("POST", "/equipments", {
        "equipmentCode": eq_code, "equipmentName": "测试空压机", "category": "动力设备",
        "model": "T-100", "department": "生产一部", "location": "测试车间"}, token=admin))
    fails("重复设备编号被拒绝", lambda: http("POST", "/equipments", {
        "equipmentCode": eq_code, "equipmentName": "重复设备",
        "department": "生产一部", "location": "测试车间"}, token=admin), (40001,))
    fails("设备编号不可修改", lambda: http("PUT", "/equipments/%s" % equipment["equipmentId"], {
        "equipmentCode": eq_code + "-X", "equipmentName": "测试空压机",
        "department": "生产一部", "location": "测试车间"}, token=admin), (40001,))
    ok("编辑设备其他字段", lambda: http("PUT", "/equipments/%s" % equipment["equipmentId"], {
        "equipmentCode": eq_code, "equipmentName": "测试空压机(改)",
        "department": "生产一部", "location": "测试车间 2 号位"}, token=admin))
    page = http("GET", "/equipments?page=1&size=50", token=rep1).data
    others = [e for e in page["items"] if e["department"] != "生产一部"]
    record("报修人只能看到本部门设备", len(others) == 0,
           "共 %d 台，全部属于生产一部" % len(page["items"]))
    ok("设备履历可查询", lambda: http("GET", "/equipments/1/history", token=admin))

    # ----------------------------------------------------------------- AT10
    print("\n--- AT10 设备二维码 ---")
    qr = http("GET", "/equipments/1/qrcode", token=admin, raw=True)
    record("生成设备二维码 PNG",
           qr.status == 200 and qr.payload.startswith(b"\x89PNG\r\n\x1a\n") and len(qr.payload) > 200,
           "HTTP %s，%d 字节，%s" % (qr.status, len(qr.payload), qr.content_type))
    items = ok("批量生成二维码", lambda: http("POST", "/equipments/qrcodes",
                                        {"equipmentIds": [1, 2, 3]}, token=admin))
    record("二维码内容为 EQ:设备编号", items[0]["content"] == "EQ:SB-001", items[0]["content"])
    fails("批量超过 50 台被拒绝",
          lambda: http("POST", "/equipments/qrcodes", {"equipmentIds": list(range(1, 52))}, token=admin),
          (40001,))
    scan = ok("扫码识别设备", lambda: http("GET", "/equipments/by-qr?content=" + q("EQ:SB-001"), token=rep1))
    record("本部门启用设备可报修", scan["canReport"] is True, scan["message"])
    scan_other = ok("扫码越部门设备", lambda: http("GET", "/equipments/by-qr?content=" + q("EQ:SB-008"), token=rep1))
    record("越部门设备只展示、不可报修", scan_other["canReport"] is False, scan_other["message"])
    fails("无效二维码内容被拒绝",
          lambda: http("GET", "/equipments/by-qr?content=ABC123", token=rep1), (40001,))
    fails("二维码对应设备不存在",
          lambda: http("GET", "/equipments/by-qr?content=" + q("EQ:NOTEXIST"), token=rep1), (40401,))

    # ----------------------------------------------------------------- AT11
    print("\n--- AT11 含照片报修 ---")
    fault_photo = ok("上传故障现场照片",
                     lambda: upload("x", "fault.jpg", "image/jpeg", "REPAIR", rep1, 1))
    record("上传返回附件标识与访问地址",
           fault_photo["attachmentId"] > 0 and bool(fault_photo["url"]), fault_photo["url"])
    fails("非图片格式被拒绝", lambda: upload("x", "not-image.txt", "text/plain", "REPAIR", rep1), (41301, 40001))
    fails("超过 5 MB 的图片被拒绝",
          lambda: upload("x", "too-big.jpg", "image/jpeg", "REPAIR", rep1), (41301, 40001))

    submit_token = str(uuid.uuid4())
    repair_body = {
        "equipmentCode": "SB-002",
        "description": "注塑机液压系统压力不足，成型件出现缩水，需现场检修。",
        "faultType": "液压气动故障",
        "urgency": "较高",
        "discoveredAt": (datetime.now() - timedelta(minutes=30)).strftime("%Y-%m-%d %H:%M:%S"),
        "attachmentIds": [fault_photo["attachmentId"]],
        "submitToken": submit_token,
        "source": "MINI",
    }
    repair = ok("提交含照片的故障报修", lambda: http("POST", "/repair-requests", repair_body, token=rep1))
    record("报修单返回编号与待受理状态",
           repair["summary"]["status"] == "待受理" and bool(repair["summary"]["requestNo"]),
           "%s / %s" % (repair["summary"]["requestNo"], repair["summary"]["status"]))
    record("报修人详情不含费用字段", repair["summary"]["totalCost"] is None, "已隐藏 totalCost")
    dup = ok("相同提交标识重试返回原单据", lambda: http("POST", "/repair-requests", repair_body, token=rep1))
    record("重试未重复创建", dup["summary"]["requestId"] == repair["summary"]["requestId"],
           "requestId=%s" % dup["summary"]["requestId"])
    fails("报修人越权查看他人报修",
          lambda: http("GET", "/repair-requests/1", token=rep2), (40302,))
    fails("报修人不能提交到其他部门设备", lambda: http("POST", "/repair-requests", {
        "equipmentCode": "SB-005", "description": "越部门测试", "faultType": "机械故障",
        "urgency": "一般", "submitToken": str(uuid.uuid4())}, token=rep1), (40302,))
    fails("故障描述为空被拒绝", lambda: http("POST", "/repair-requests", {
        "equipmentCode": "SB-002", "description": "", "faultType": "机械故障",
        "urgency": "一般", "submitToken": str(uuid.uuid4())}, token=rep1), (40001,))
    fails("故障类型不在字典内被拒绝", lambda: http("POST", "/repair-requests", {
        "equipmentCode": "SB-002", "description": "测试故障类型", "faultType": "未知类型",
        "urgency": "一般", "submitToken": str(uuid.uuid4())}, token=rep1), (40001,))

    pending_list = ok("管理员查询待受理报修",
                      lambda: http("GET", "/repair-requests?status=" + q("待受理") + "&page=1&size=50", token=admin))
    record("待受理列表包含新报修",
           any(r["requestId"] == repair["summary"]["requestId"] for r in pending_list["items"]),
           "共 %s 条" % pending_list["total"])

    # ----------------------------------------------------------------- AT03
    print("\n--- AT03 受理与派工 ---")
    order = ok("受理并派工", lambda: http("POST", "/repair-requests/%s/accept" % repair["summary"]["requestId"],
                                     {"ownerId": 2, "plannedDate": TODAY, "opinion": "现场检修"}, token=admin))
    record("生成唯一维修工单",
           bool(order["orderNo"]) and order["status"] == "待处理",
           "%s / %s" % (order["orderNo"], order["status"]))
    fails("重复受理被拒绝",
          lambda: http("POST", "/repair-requests/%s/accept" % repair["summary"]["requestId"],
                       {"ownerId": 2, "plannedDate": TODAY}, token=admin), (40901,))
    fails("不存在的报修不能受理",
          lambda: http("POST", "/repair-requests/999999/accept",
                       {"ownerId": 2, "plannedDate": TODAY}, token=admin), (40401,))

    repair2 = ok("提交第二条报修（生产二部）", lambda: http("POST", "/repair-requests", {
        "equipmentCode": "SB-005", "description": "输送带跑偏，需要调整张紧装置。",
        "faultType": "机械故障", "urgency": "一般",
        "submitToken": str(uuid.uuid4()), "source": "WEB"}, token=rep2))
    fails("驳回原因为空被拒绝",
          lambda: http("POST", "/repair-requests/%s/reject" % repair2["summary"]["requestId"],
                       {"reason": ""}, token=admin), (40001,))
    ok("驳回无效报修", lambda: http("POST", "/repair-requests/%s/reject" % repair2["summary"]["requestId"],
                               {"reason": "重复报修，已由现场人员处理"}, token=admin))
    rejected = ok("查看驳回结果",
                  lambda: http("GET", "/repair-requests/%s" % repair2["summary"]["requestId"], token=admin))
    record("驳回后展示原因且无工单",
           rejected["summary"]["status"] == "已驳回" and bool(rejected["decisionReason"])
           and rejected["order"] is None, rejected["decisionReason"])

    # ----------------------------------------------------------------- AT04
    print("\n--- AT04 执行、退回与验收 ---")
    mine = ok("维修人员查询本人工单", lambda: http("GET", "/work-orders/mine?page=1&size=50", token=tech1))
    record("本人工单包含新派工单",
           any(o["orderId"] == order["orderId"] for o in mine["items"]), "共 %s 条" % mine["total"])
    fails("非负责人开工被拒绝",
          lambda: http("POST", "/work-orders/%s/start" % order["orderId"], token=tech3), (40302,))
    fails("未开工直接提交完工被拒绝",
          lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], {
              "result": "提前提交", "totalCost": 10, "attachmentIds": [],
              "submitToken": str(uuid.uuid4())}, token=tech1), (42201,))
    ok("开工", lambda: http("POST", "/work-orders/%s/start" % order["orderId"], token=tech1))
    fails("重复开工被拒绝",
          lambda: http("POST", "/work-orders/%s/start" % order["orderId"], token=tech1), (42201,))
    work_photo = ok("上传维修完成照片",
                    lambda: upload("x", "work.jpg", "image/jpeg", "WORK", tech1, 1))
    fails("无完工照片不能提交", lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], {
        "result": "已更换密封件", "totalCost": 120.5, "attachmentIds": [],
        "submitToken": str(uuid.uuid4())}, token=tech1), (40001,))
    fails("费用为负数被拒绝", lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], {
        "result": "已处理", "totalCost": -1, "attachmentIds": [work_photo["attachmentId"]],
        "submitToken": str(uuid.uuid4())}, token=tech1), (40001,))
    fails("费用超过上限被拒绝", lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], {
        "result": "已处理", "totalCost": 100000000, "attachmentIds": [work_photo["attachmentId"]],
        "submitToken": str(uuid.uuid4())}, token=tech1), (40001,))
    submit_body = {
        "cause": "液压油缸密封圈老化导致内泄",
        "process": "拆检油缸，更换密封圈，补加液压油并试压",
        "result": "压力恢复至 14MPa，试生产 20 件合格",
        "replacedParts": "密封圈套件 ×1",
        "totalCost": 120.50,
        "attachmentIds": [work_photo["attachmentId"]],
        "submitToken": str(uuid.uuid4()),
    }
    submitted = ok("提交完工（含完工照片）",
                   lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], submit_body, token=tech1))
    record("工单转为待验收并记录费用",
           submitted["summary"]["status"] == "待验收" and float(submitted["summary"]["totalCost"]) == 120.50,
           "%s / %s" % (submitted["summary"]["status"], submitted["summary"]["totalCost"]))
    fails("重复提交不产生第二条记录",
          lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], submit_body, token=tech1),
          (40901, 42201))

    pending_accept = ok("管理员查询待验收工单",
                        lambda: http("GET", "/acceptances/pending?page=1&size=50", token=admin))
    record("待验收列表包含该工单",
           any(o["orderId"] == order["orderId"] for o in pending_accept["items"]),
           "共 %s 条" % pending_accept["total"])
    fails("退回未填写原因被拒绝",
          lambda: http("POST", "/work-orders/%s/acceptance" % order["orderId"],
                       {"result": "RETURN", "opinion": ""}, token=admin), (40001,))
    ok("验收退回整改", lambda: http("POST", "/work-orders/%s/acceptance" % order["orderId"],
                               {"result": "RETURN", "opinion": "压力表读数仍偏低，请复查溢流阀"},
                               token=admin))
    after_return = ok("查看退回后的工单",
                      lambda: http("GET", "/work-orders/%s" % order["orderId"], token=admin))
    record("退回后回到处理中且保留验收历史",
           after_return["summary"]["status"] == "处理中" and len(after_return["acceptances"]) == 1,
           after_return["summary"]["status"])

    work_photo2 = ok("整改后重新上传完工照片",
                     lambda: upload("x", "work.jpg", "image/jpeg", "WORK", tech1))
    ok("整改后再次提交完工", lambda: http("POST", "/work-orders/%s/submit" % order["orderId"], {
        "cause": "溢流阀调压弹簧疲劳", "process": "更换调压弹簧并重新整定压力",
        "result": "压力稳定在 16MPa，连续运行 30 分钟无异常", "totalCost": 150.00,
        "attachmentIds": [work_photo2["attachmentId"]],
        "submitToken": str(uuid.uuid4())}, token=tech1))
    ok("验收通过", lambda: http("POST", "/work-orders/%s/acceptance" % order["orderId"],
                            {"result": "PASS", "opinion": "整改到位，验收通过"}, token=admin))
    done_order = ok("查看已完成工单", lambda: http("GET", "/work-orders/%s" % order["orderId"], token=admin))
    record("工单已完成并保留两次处理记录",
           done_order["summary"]["status"] == "已完成" and len(done_order["records"]) == 2,
           "%s，处理记录 %d 条" % (done_order["summary"]["status"], len(done_order["records"])))
    done_repair = ok("关联报修同步完成",
                     lambda: http("GET", "/repair-requests/%s" % repair["summary"]["requestId"], token=admin))
    record("报修单状态同步为已完成", done_repair["summary"]["status"] == "已完成", done_repair["summary"]["status"])
    fails("已完成工单不能重复验收",
          lambda: http("POST", "/work-orders/%s/acceptance" % order["orderId"],
                       {"result": "PASS", "opinion": "再次验收"}, token=admin), (42201,))

    # ----------------------------------------------------------------- AT05
    print("\n--- AT05 保养任务 ---")
    maintain_token = str(uuid.uuid4())
    maintenance_body = {
        "equipmentId": 6,
        "taskContent": "焊接机器人季度保养：清理焊枪、校准 TCP、检查电缆",
        "plannedDate": TOMORROW,
        "ownerId": 3,
        "createToken": maintain_token,
    }
    maintenance = ok("安排保养任务", lambda: http("POST", "/work-orders", maintenance_body, token=admin))
    record("保养工单初始为待处理且无关联报修",
           maintenance["summary"]["status"] == "待处理" and maintenance["summary"]["requestId"] is None,
           "%s / %s" % (maintenance["summary"]["orderNo"], maintenance["summary"]["status"]))
    dup_maint = ok("相同创建标识重试返回原工单",
                   lambda: http("POST", "/work-orders", maintenance_body, token=admin))
    record("保养任务未重复创建", dup_maint["summary"]["orderId"] == maintenance["summary"]["orderId"],
           "orderId=%s" % dup_maint["summary"]["orderId"])
    ok("保养工单开工", lambda: http("POST", "/work-orders/%s/start" % maintenance["summary"]["orderId"],
                              token=tech2))
    maint_photo = ok("上传保养完成照片",
                     lambda: upload("x", "work.jpg", "image/jpeg", "WORK", tech2))
    ok("保养工单提交完工", lambda: http("POST", "/work-orders/%s/submit" % maintenance["summary"]["orderId"], {
        "process": "清理焊枪喷嘴，校准 TCP 偏差，检查示教器与电缆",
        "result": "保养完成，TCP 校准偏差 0.3mm，运行正常", "totalCost": 88.00,
        "attachmentIds": [maint_photo["attachmentId"]],
        "submitToken": str(uuid.uuid4())}, token=tech2))
    ok("保养验收通过", lambda: http("POST", "/work-orders/%s/acceptance" % maintenance["summary"]["orderId"],
                              {"result": "PASS", "opinion": "保养项目完整"}, token=admin))
    history = ok("设备履历查询", lambda: http("GET", "/equipments/6/history", token=admin))
    record("设备历史包含保养工单",
           any(o["orderId"] == maintenance["summary"]["orderId"] for o in history["orders"]),
           "历史工单 %d 条" % len(history["orders"]))

    # ----------------------------------------------------------------- AT06
    print("\n--- AT06 统计 ---")
    stats = ok("管理员统计", lambda: http("GET", "/statistics?from=%s&to=%s" % (TODAY, TODAY), token=admin))
    record("完成数量与维修费用统计有效",
           stats["doneRepairCount"] >= 1 and float(stats["repairCostTotal"]) >= 150.0,
           "维修完成 %s 单，费用 %s 元，保养完成 %s 单，未完成工单 %s 单"
           % (stats["doneRepairCount"], stats["repairCostTotal"],
              stats["doneMaintenanceCount"], stats["unfinishedOrderCount"]))
    fails("报修人不能查看统计", lambda: http("GET", "/statistics", token=rep1), (40301,))
    fails("日期范围倒置被拒绝",
          lambda: http("GET", "/statistics?from=%s&to=%s" % (TOMORROW, TODAY), token=admin), (40001,))

    # ----------------------------------------------------------------- AT12
    print("\n--- AT12 照片鉴权与两端一致 ---")
    admin_photo = http("GET", "/files/%s" % fault_photo["attachmentId"], token=admin, raw=True)
    record("管理员可查看故障照片", admin_photo.status == 200, "HTTP %s" % admin_photo.status)
    rep_photo = http("GET", "/files/%s" % fault_photo["attachmentId"], token=rep1, raw=True)
    record("报修人可查看本单照片", rep_photo.status == 200, "HTTP %s" % rep_photo.status)
    fails("其他报修人不能查看他人照片",
          lambda: http("GET", "/files/%s" % fault_photo["attachmentId"], token=rep2), (40302,))
    tech_photo = http("GET", "/files/%s" % work_photo["attachmentId"], token=tech1, raw=True)
    record("维修人员可查看本人工单完工照片", tech_photo.status == 200, "HTTP %s" % tech_photo.status)
    fails("非本单维修人员不能查看完工照片",
          lambda: http("GET", "/files/%s" % work_photo["attachmentId"], token=tech3), (40302,))
    thumb = http("GET", "/files/%s?width=200" % fault_photo["attachmentId"], token=admin, raw=True)
    record("缩略图接口可用", thumb.status == 200 and len(thumb.payload) > 200,
           "%d 字节，%s" % (len(thumb.payload), thumb.content_type))

    verify = ok("扫码核对工单设备一致",
                lambda: http("GET", "/equipments/verify?orderId=%s&content=%s"
                             % (order["orderId"], q("EQ:SB-002")), token=tech1))
    record("核对结果为一致", verify["matched"] is True, verify["message"])
    verify_bad = ok("扫码核对工单设备不一致",
                    lambda: http("GET", "/equipments/verify?orderId=%s&content=%s"
                                 % (order["orderId"], q("EQ:SB-005")), token=tech1))
    record("核对结果为不一致", verify_bad["matched"] is False, verify_bad["message"])

    # ------------------------------------------------------- 设备停用与依赖
    print("\n--- 设备停用依赖校验 ---")
    fails("存在待受理报修的设备不能停用",
          lambda: http("POST", "/equipments/3/disable", token=admin), (40901,))
    ok("无关联业务的设备可停用",
       lambda: http("POST", "/equipments/%s/disable" % equipment["equipmentId"], token=admin))
    fails("停用设备不能提交报修", lambda: http("POST", "/repair-requests", {
        "equipmentCode": eq_code, "description": "停用设备测试", "faultType": "机械故障",
        "urgency": "一般", "submitToken": str(uuid.uuid4())}, token=rep1), (40902,))
    ok("重新启用设备", lambda: http("POST", "/equipments/%s/enable" % equipment["equipmentId"], token=admin))

    # ------------------------------------------------------------- 会话退出
    print("\n--- 会话退出 ---")
    logout_token = login("admin", "admin123")
    ok("退出登录", lambda: http("POST", "/auth/logout", token=logout_token))
    fails("退出后令牌失效", lambda: http("GET", "/auth/profile", token=logout_token), (40101,))

    # ------------------------------------------------------------- 生成报告
    total = PASSED + FAILED
    header = [
        "# 设备维修保养系统 冒烟测试报告",
        "",
        "- 运行时间：%s" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "- 接口地址：%s" % BASE,
        "- 数据基线：20 台设备、6 个账号的演示数据 + 本次测试新增单据",
        "",
        "| 结果 | 用例 | 说明 |",
        "| --- | --- | --- |",
    ]
    footer = [
        "",
        "**合计：%d 项，通过 %d 项，失败 %d 项。**" % (total, PASSED, FAILED),
        "",
        "全部用例通过，覆盖 AT01 ~ AT12 的主要验收场景。" if FAILED == 0
        else "存在失败用例，请结合后端日志 logs/sems.log 排查。",
        "",
    ]
    with open(args.report, "w", encoding="utf-8") as fh:
        fh.write("\n".join(header + REPORT + footer))
    print("\n合计 %d 项：通过 %d，失败 %d。报告：%s" % (total, PASSED, FAILED, args.report))
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as exc:
        print("\n测试中断：%s" % exc)
        sys.exit(1)
