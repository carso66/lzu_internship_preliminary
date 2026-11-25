#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
作业 AI 使用取证脚本
====================
目的：在作业指定平台（xyb2b / xytoken）上产生**真实、可核对**的 AI 调用记录，
      并把每次调用的 token 数、耗时、费用完整记下来，用于填写 AI_USAGE.md。

用法：
    python run_ai_review.py

前置：把 API Key 存到下面 KEY_PATHS 里的任意一个文件（只写 key 本身，不要引号不要换行）
      Windows: C:\\Users\\Lenovo\\.secrets\\xytoken.key
      或者同目录下 key.txt

设计原则：
1. 分档用模型 —— 机械整理用 Flash 级，推理/审查用 Pro 级，对应评分表「模型选型」
2. 每个场景的 prompt 都是真问题，不是凑数
3. 记录真实 usage，不估算、不编造
4. 不回显 Key
"""

import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

try:
    import requests
except ImportError:
    print("[FATAL] 缺少 requests：pip install requests")
    sys.exit(1)

# Windows 控制台中文
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE_URL = "https://api.xytoken.xyb2b.com/v1/chat/completions"

HERE = Path(__file__).resolve().parent
TESTING = HERE.parent          # repo/testing
REPO = TESTING.parent          # repo
OUT_RAW = HERE / "raw"

KEY_PATHS = [
    Path.home() / ".secrets" / "xytoken.key",
    HERE / "key.txt",
    REPO / ".xytoken.key",
]

# 平台价格页公示单价（USD / 百万 token）
# 注意：这里的币种是美元，不是人民币。平台 500000 quota = 1 USD，且 1 USD = 6.75 CNY。
# 早期版本误把这些单价当成人民币，导致费用被少算 6.75 倍，已在文档中修正。
USD_TO_CNY = 6.75
PRICE = {
    "qwen3.8-flash":      {"in": 0.088889, "out": 0.300000},
    "glm-5.3-flash":      {"in": 0.112222, "out": 0.374444},
    "deepseek-v4.1-flash": {"in": 0.225556, "out": 0.900000},
    "glm-5.3":            {"in": 0.888889, "out": 3.111111},
    "deepseek-v4-pro":    {"in": 0.992222, "out": 2.977778},
    "kimi-k3.1-pro":      {"in": 1.444444, "out": 7.555556},
}


def load_key():
    for p in KEY_PATHS:
        if p.exists():
            k = p.read_text(encoding="utf-8", errors="ignore").strip()
            k = k.strip('"').strip("'").split()[0] if k else ""
            if k:
                print(f"[key] 已从 {p} 读取（长度 {len(k)}，不回显）")
                return k
    print("[FATAL] 没有找到 API Key 文件。请先创建：")
    print(f"        {KEY_PATHS[0]}")
    print("        文件内容只写 key 本身，例如：sk-xxxxxxxx")
    sys.exit(2)


def read_snippet(path, start=None, end=None, limit=None):
    """从文档里取一段，避免把整个大文件塞给模型烧钱。"""
    if not path.exists():
        return f"（文件不存在：{path}）"
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    if start is not None or end is not None:
        lines = lines[(start or 1) - 1: end]
    text = "\n".join(lines)
    if limit and len(text) > limit:
        text = text[:limit] + "\n...（后略）"
    return text


def call(key, model, prompt, max_tokens=8000, temperature=0.3, timeout=600, retries=2):
    """发一次调用，返回 (正文, usage, 耗时)。

    注意：本平台的白名单模型（GLM-5.3 / Kimi-K3.1-Pro / deepseek-v4-pro /
    Qwen3.8-flash）都是推理模型，输出 token 里绝大部分是 reasoning_tokens。
    max_tokens 给太小会导致思考没结束就被截断，正文为空白，因此统一放宽。
    """
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是资深测试工程师，回答直接、具体、不客套。用中文。"},
            {"role": "user", "content": prompt},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }

    last_err = None
    for attempt in range(1, retries + 2):
        t0 = time.time()
        try:
            r = requests.post(BASE_URL, headers=headers, json=payload, timeout=timeout)
        except Exception as e:
            dt = time.time() - t0
            last_err = f"{type(e).__name__}: {e}"
            print(f"      第 {attempt} 次失败（{dt:.0f}s）：{last_err}")
            if attempt <= retries:
                print(f"      等待 5s 后重试 ...")
                time.sleep(5)
                continue
            raise RuntimeError(last_err)
        dt = time.time() - t0

        if r.status_code != 200:
            last_err = f"HTTP {r.status_code}: {r.text[:300]}"
            if r.status_code in (429, 500, 502, 503, 504) and attempt <= retries:
                print(f"      第 {attempt} 次返回 {r.status_code}，5s 后重试 ...")
                time.sleep(5)
                continue
            raise RuntimeError(last_err)

        body = r.json()
        msg = (body.get("choices") or [{}])[0].get("message") or {}
        text = msg.get("content") or ""
        usage = body.get("usage", {}) or {}
        rid = body.get("id", "")
        return text, usage, dt, rid, body

    raise RuntimeError(last_err or "unknown error")


def cost_of(model, usage):
    """返回 (usd, cny)。PRICE 的单价是 USD / 百万 token。"""
    p = PRICE.get(model)
    if not p:
        return None
    pin = usage.get("prompt_tokens", 0) or 0
    pout = usage.get("completion_tokens", 0) or 0
    usd = pin / 1_000_000 * p["in"] + pout / 1_000_000 * p["out"]
    return usd


# ---------------------------------------------------------------- 场景定义
def build_scenarios():
    s = []

    # 场景 A：机械整理（Flash 级）—— 把真实证据整理成对照表
    ev = read_snippet(TESTING / "缺陷报告.md", limit=2600)
    s.append({
        "id": "A",
        "title": "把缺陷原始证据整理成对照表",
        "tier": "Flash 级（机械整理，不值得用强模型）",
        "model": "qwen3.8-flash",
        "max_tokens": 6000,
        "prompt": (
            "下面是一份缺陷报告的开头部分，包含缺陷清单和 BUG-01 的完整证据。\n"
            "请只做一件事：把其中出现的**接口路径、HTTP 方法、请求参数、实际响应关键字段**提取出来，"
            "整理成一张 markdown 三列表格（接口 | 请求要点 | 观察到的异常）。\n\n"
            "严格约束：\n"
            "1. 只允许使用原文中出现的信息，**不得补充、不得推断、不得修正**任何你认为不合理的地方\n"
            "2. 如果某处原文没写清楚，就写「原文未给出」\n"
            "3. 不要写总结段落，不要给建议，只输出表格\n\n"
            f"原文：\n{ev}"
        ),
    })

    # 场景 B：优先级复核（Pro 级）—— 让它挑战我的 S1 定级
    idx = read_snippet(TESTING / "缺陷报告.md", start=1, end=26, limit=2500)
    s.append({
        "id": "B",
        "title": "复核 17 条缺陷的严重程度定级是否合理",
        "tier": "Pro 级（评级需要理解业务影响，值得上强模型）",
        "model": "deepseek-v4-pro",
        "max_tokens": 6000,
        "prompt": (
            "这是一个校园二手市集电商服务的缺陷清单（共 17 条，S1=数据/资金/隐私直接受损，"
            "S2=业务逻辑错误或可被滥用，S3=健壮性）。\n\n"
            "请你扮演一个**唱反调的评审**：找出其中**定级偏高或偏低**的条目，"
            "每条给出「编号 + 你认为应该是 S几 + 一句话理由」。\n\n"
            "要求：\n"
            "- 至少认真评估 5 条，不要一律认同\n"
            "- 如果某条你认为定得完全正确，跳过不写\n"
            "- 只输出 markdown 表格：编号 | 我的定级 | 你的定级 | 理由\n"
            "- 不要写开场白和结尾总结\n\n"
            f"{idx}"
        ),
    })

    # 场景 C：代码审查对照（Pro 级）
    code = read_snippet(TESTING / "legacy" / "order_service.py", limit=3000)
    code2 = read_snippet(TESTING / "legacy" / "payment_service.py", limit=3000)
    s.append({
        "id": "C",
        "title": "独立审查 legacy 订单/支付代码，与我的审查结论对照",
        "tier": "Pro 级（代码审查要看因果链）",
        "model": "glm-5.3",
        "max_tokens": 6000,
        "prompt": (
            "审查下面两段 Python 代码，找出其中的缺陷。请**独立判断**，不要假设代码是正确的。\n\n"
            "输出要求：\n"
            "1. 每条格式：`文件名:行号` | 问题类别 | 为什么会出问题 | 修复方向\n"
            "2. 最多 8 条，只写你**有把握**的，不要凑数\n"
            "3. 按严重程度从高到低排\n"
            "4. 不要写代码，不要写完整修复实现\n\n"
            f"=== order_service.py ===\n{code}\n\n=== payment_service.py ===\n{code2}"
        ),
    })

    # 场景 D：用例覆盖盲区（Pro 级）
    cases = read_snippet(TESTING / "测试用例.md", limit=4200)
    s.append({
        "id": "D",
        "title": "找出测试用例的覆盖盲区",
        "tier": "Pro 级（覆盖分析需要推理）",
        "model": "kimi-k3.1-pro",
        "max_tokens": 6000,
        "prompt": (
            "下面是我为一个校园市集 API 写的测试用例（覆盖登录、商品、订单、支付、评价、管理端）。\n"
            "被测服务有 16 个接口，已知存在参数校验缺失、越权、并发超卖、支付回调无验签等问题。\n\n"
            "请指出**我的用例漏掉了哪些重要测试点**，按「漏掉的场景 + 为什么重要 + 具体怎么测（一句话）」列出。\n\n"
            "约束：\n"
            "- 最多 6 条，按重要性排序\n"
            "- 只提我在用例表里**确实没有覆盖**的，不要重复已有条目\n"
            "- 不要输出表格以外的寒暄\n\n"
            f"{cases}"
        ),
    })

    # 场景 E：反向挑战（Pro 级）—— 体现「会否掉 AI 的建议」
    s.append({
        "id": "E",
        "title": "让 AI 为「并发超卖不算缺陷」辩护，再判断它是否成立",
        "tier": "Pro 级（用来验证 AI 的边界，不是求答案）",
        "model": "deepseek-v4-pro",
        "max_tokens": 5000,
        "prompt": (
            "一个电商服务在并发下把库存 1 的商品卖出了 8 件，库存变成 -7。"
            "有开发认为这不算缺陷，理由是「这不是真实互联网流量，是压测人为造出来的，"
            "而且数据库最终会把负数修正」。\n\n"
            "请你**尽力为这个观点辩护**，列出你认为最有力的 3 条论据。"
            "然后给出你自己的结论：这个辩护成立吗？如果站不住，最关键的反驳点是什么？\n\n"
            "要求：辩护部分要真诚地强，结论部分要明确，不要和稀泥。"
        ),
    })

    return s


def main():
    key = load_key()
    OUT_RAW.mkdir(parents=True, exist_ok=True)

    scenarios = build_scenarios()
    print()
    print("=" * 66)
    print(f"  将执行 {len(scenarios)} 个场景，覆盖 Flash 级与 Pro 级模型")
    print("=" * 66)
    print()

    results = []
    total_cost = 0.0
    total_in = total_out = 0

    for sc in scenarios:
        print(f"[{sc['id']}] {sc['title']}")
        print(f"      模型: {sc['model']}  （{sc['tier']}）")
        try:
            text, usage, dt, rid, body = call(
                key, sc["model"], sc["prompt"], max_tokens=sc["max_tokens"]
            )
            c = cost_of(sc["model"], usage)
            pin = usage.get("prompt_tokens", 0) or 0
            pout = usage.get("completion_tokens", 0) or 0
            total_in += pin
            total_out += pout
            if c:
                total_cost += c

            (OUT_RAW / f"scenario_{sc['id']}.json").write_text(
                json.dumps({
                    "scenario": sc["id"],
                    "title": sc["title"],
                    "model": sc["model"],
                    "prompt": sc["prompt"],
                    "response_id": rid,
                    "usage": usage,
                    "elapsed_sec": round(dt, 2),
                    "cost_est_cny": round(c, 6) if c else None,
                    "content": text,
                    "raw": body,
                }, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

            print(f"      OK  输入 {pin} tok / 输出 {pout} tok / {dt:.1f}s "
                  f"/ {c:.5f} USD = {c * USD_TO_CNY:.4f} 元")
            print(f"      正文 {len(text)} 字符 / request_id: {rid}")
            results.append({**sc, "ok": True, "usage": usage, "elapsed": dt,
                            "cost": c, "content": text, "rid": rid})
        except Exception as e:
            print(f"      FAIL {type(e).__name__}: {e}")
            results.append({**sc, "ok": False, "error": str(e)})
        print()

    # ---------- 汇总 ----------
    ok = [r for r in results if r.get("ok")]
    models_used = sorted({r["model"] for r in ok})

    summary = {
        "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "endpoint": BASE_URL,
        "calls_total": len(results),
        "calls_ok": len(ok),
        "models_used": models_used,
        "prompt_tokens": total_in,
        "completion_tokens": total_out,
        "total_tokens": total_in + total_out,
        "cost_est_cny": round(total_cost, 6),
        "scenarios": [
            {
                "id": r["id"], "title": r["title"], "model": r["model"],
                "tier": r["tier"], "ok": r["ok"],
                "prompt_tokens": (r.get("usage") or {}).get("prompt_tokens"),
                "completion_tokens": (r.get("usage") or {}).get("completion_tokens"),
                "elapsed_sec": round(r.get("elapsed", 0), 2),
                "cost_est_cny": round(r["cost"], 6) if r.get("cost") else None,
                "request_id": r.get("rid"),
            }
            for r in results
        ],
    }
    (HERE / "汇总.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # ---------- 人可读日志 ----------
    md = []
    md.append("# AI 调用记录（平台原始日志）\n")
    md.append(f"> 生成时间：{summary['generated_at']}　"
              f"　端点：`{BASE_URL}`\n")
    md.append(f"> 本文由脚本 `run_ai_review.py` 自动生成，"
              f"每次调用的 `request_id` 与平台控制台日志一一对应，可直接核对。\n")

    md.append("## 汇总\n")
    md.append("| 项 | 值 |")
    md.append("|---|---|")
    md.append(f"| 调用次数 | {summary['calls_total']}（成功 {summary['calls_ok']}） |")
    md.append(f"| 使用模型 | {', '.join('`'+m+'`' for m in models_used)} |")
    md.append(f"| 输入 token | {summary['prompt_tokens']:,} |")
    md.append(f"| 输出 token | {summary['completion_tokens']:,} |")
    md.append(f"| 合计 token | {summary['total_tokens']:,} |")
    md.append(f"| 估算费用 | **{summary['cost_est_cny'] * USD_TO_CNY:.4f} 元**"
              f"（{summary['cost_est_cny']:.6f} USD，按 1 USD = {USD_TO_CNY} CNY） |")
    md.append("")

    md.append("## 调用明细\n")
    md.append("| # | 场景 | 模型 | 档位 | 输入 | 输出 | 耗时 | 估算费用 | request_id |")
    md.append("|---|---|---|---|---|---|---|---|---|")
    for r in summary["scenarios"]:
        cost_txt = ("%.5f USD / %.4f 元" % (r["cost_est_cny"], r["cost_est_cny"] * USD_TO_CNY)) if r.get("cost_est_cny") else "-"
        md.append(
            f"| {r['id']} | {r['title']} | `{r['model']}` | {r['tier'].split('（')[0]} "
            f"| {r['prompt_tokens'] or '-'} | {r['completion_tokens'] or '-'} "
            f"| {r['elapsed_sec']}s | {cost_txt} "
            f"| `{(r['request_id'] or '')[:24]}` |"
        )
    md.append("")

    md.append("## 各场景问答原文\n")
    for r in results:
        if not r.get("ok"):
            md.append(f"### 场景 {r['id']}　{r['title']}\n")
            md.append(f"**调用失败**：`{r.get('error','')}`\n")
            continue
        md.append(f"### 场景 {r['id']}　{r['title']}\n")
        md.append(f"- 模型：`{r['model']}`　档位：{r['tier']}")
        md.append(f"- token：输入 {r['usage'].get('prompt_tokens')} / "
                  f"输出 {r['usage'].get('completion_tokens')}　耗时 {r['elapsed']:.1f}s")
        md.append(f"- request_id：`{r['rid']}`\n")
        md.append("**提问（节选）**\n")
        pv = r["prompt"]
        md.append("```text")
        md.append(pv[:700] + ("\n...（后续为原文粘贴）" if len(pv) > 700 else ""))
        md.append("```\n")
        md.append("**回答**\n")
        md.append(r["content"])
        md.append("\n---\n")

    (HERE / "调用记录.md").write_text("\n".join(md), encoding="utf-8")

    # ---------- 终端结论 ----------
    print("=" * 66)
    print("  完成")
    print("=" * 66)
    print(f"  成功调用   : {len(ok)} / {len(results)}")
    print(f"  使用模型   : {', '.join(models_used)}")
    print(f"  合计 token : {total_in + total_out:,}  (输入 {total_in:,} / 输出 {total_out:,})")
    print(f"  估算费用   : {total_cost:.6f} USD = {total_cost * USD_TO_CNY:.4f} 元 / 额度 40 元")
    print()
    print(f"  明细已写入 : {HERE / '调用记录.md'}")
    print(f"  汇总 JSON  : {HERE / '汇总.json'}")
    print(f"  原始响应   : {OUT_RAW}")
    print()
    print("  下一步：把 汇总.json 里的数字填进 AI_USAGE.md，或直接让我来填。")


if __name__ == "__main__":
    main()
