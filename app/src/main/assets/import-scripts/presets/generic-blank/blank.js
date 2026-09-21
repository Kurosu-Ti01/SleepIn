// SleepIn 教务导入 · 内置演示脚本
//
// 这个脚本不抓取任何网页，而是用固定的示例数据走完整个导入流程，用来确认下面这条链路是通的：
//
//   WebView 容器 → JS 桥（alert / prompt / choose）→ commit → 落库 → 课表显示
//
// 在没有适配脚本、或只想确认功能是否正常时使用它。它同时是本仓库导入脚本的参考样例：
// payload 结构、桥 API 的完整说明见文档「导入脚本编写指南」。

// ---------------------------------------------------------------------------
// 示例数据
// ---------------------------------------------------------------------------

// 前三门课落在常规节次内；最后一门故意使用第 13 节，用来检查「作息表缺节次」时的提示与占位行。
const DEMO_COURSES = [
  {
    name: "演示课程 · 高等数学",
    teacher: "示例教师",
    note: null,
    color: null,
    sessions: [
      { day: 1, from: 1, to: 2, location: "示例楼 A101", weeks: weeksInclusive(1, 16) },
      { day: 3, from: 3, to: 4, location: "示例楼 A101", weeks: weeksInclusive(1, 16) }
    ]
  },
  {
    name: "演示课程 · 大学英语",
    teacher: "示例教师",
    note: null,
    color: null,
    sessions: [
      { day: 2, from: 5, to: 6, location: "示例楼 B203", weeks: weeksInclusive(1, 8) }
    ]
  },
  {
    name: "演示课程 · 第 13 节（用于检查作息表提示）",
    teacher: null,
    note: "若所选作息表只定义到第 12 节，首页会提示补全作息表，这一行会显示为“未配置”。",
    color: null,
    sessions: [
      { day: 5, from: 13, to: 13, location: null, weeks: weeksInclusive(1, 16) }
    ]
  }
];

function weeksInclusive(from, to) {
  const weeks = [];
  for (let week = from; week <= to; week++) {
    weeks.push(week);
  }
  return weeks;
}

// ---------------------------------------------------------------------------
// 输入校验
//
// 桥约定：校验函数返回 null（或空串）表示通过，返回字符串则表示错误提示，
// 该提示会显示在输入框下方，弹窗不会关闭。
// ---------------------------------------------------------------------------

function validateYear(input) {
  return /^[0-9]{4}$/.test(input) ? null : "请输入四位数字的学年，例如 2024";
}

// ---------------------------------------------------------------------------
// 导入流程
//
// 每个用户交互都可能被取消：取消时 alert 返回 false，prompt / choose 返回 null，此时必须立即结束
// 流程，不能继续往下提交数据。commit 只在数据真正写入成功后才 resolve true，用户取消预览或写入失败
// 都会 reject；两种都要判，不能把「没写成功」当成功。只有全部成功后才调用 finish()。
// ---------------------------------------------------------------------------

async function runImport() {
  window.sleepinBridge.toast("演示导入开始");

  const confirmed = await window.sleepinBridgePromise.alert({
    title: "演示导入",
    message: "这个方案不会访问任何网站，只会写入几门示例课程，用来确认导入功能是否正常。",
    confirmText: "开始演示"
  });
  if (confirmed !== true) {
    window.sleepinBridge.toast("已取消演示导入");
    return;
  }

  const year = await window.sleepinBridgePromise.prompt({
    title: "学年",
    hint: "用于生成课程表名称",
    initialValue: String(new Date().getFullYear()),
    validate: "validateYear"
  });
  if (year === null) {
    window.sleepinBridge.toast("已取消演示导入");
    return;
  }

  const termIndex = await window.sleepinBridgePromise.choose({
    title: "学期",
    options: ["第一学期", "第二学期"],
    defaultIndex: 0
  });
  if (termIndex === null) {
    window.sleepinBridge.toast("已取消演示导入");
    return;
  }

  // 开学日期取本周一，这样导入后当前周就是第 1 周，示例课程立刻可见。
  const payload = {
    schema: 1,
    timetable: {
      name: year + " 演示课程表（" + (termIndex === 0 ? "第一学期" : "第二学期") + "）",
      totalWeeks: 16,
      startDate: mondayOfThisWeek()
    },
    courses: DEMO_COURSES
  };

  // 桥约定：commit 只在数据真正写入成功后才 resolve true，用户取消预览或写入失败都会 reject。
  // 两种都判，避免把「没写成功」当成成功。
  let committed = false;
  try {
    committed = (await window.sleepinBridgePromise.commit(payload)) === true;
  } catch (error) {
    window.sleepinBridge.toast("导入失败：" + error);
    return;
  }
  if (!committed) {
    window.sleepinBridge.toast("导入未完成");
    return;
  }

  window.sleepinBridge.toast("演示导入完成");
  window.sleepinBridge.finish();
}

function mondayOfThisWeek() {
  const now = new Date();
  const daysSinceMonday = (now.getDay() + 6) % 7;
  const monday = new Date(now.getTime() - daysSinceMonday * 24 * 60 * 60 * 1000);
  return monday.getFullYear() + "-" + pad2(monday.getMonth() + 1) + "-" + pad2(monday.getDate());
}

function pad2(value) {
  return value < 10 ? "0" + value : String(value);
}

runImport();
