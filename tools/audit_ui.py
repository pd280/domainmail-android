"""UI 源码静态审计：图标导入、引用、括号平衡。仅做文本检查，不编译。"""
import re
from pathlib import Path

p = Path("android/app/src/main/java/com/ifwqu/domainmail/ui/Screens.kt")
s = p.read_text(encoding="utf-8")

# 用字符扫描剥离注释与字符串，避免正则转义问题
clean = []
i = 0
n = len(s)
while i < n:
    c = s[i]
    if c == "/" and i + 1 < n and s[i + 1] == "/":
        j = s.find("\n", i)
        i = n if j < 0 else j
        continue
    if c == "/" and i + 1 < n and s[i + 1] == "*":
        j = s.find("*/", i + 2)
        i = n if j < 0 else j + 2
        continue
    if c == '"':
        i += 1
        while i < n:
            if s[i] == "\\":
                i += 2
                continue
            if s[i] == '"':
                i += 1
                break
            i += 1
        continue
    clean.append(c)
    i += 1
code = "".join(clean)

for a, b, name in [("(", ")", "paren"), ("{", "}", "brace"), ("[", "]", "bracket")]:
    ok = code.count(a) == code.count(b)
    print(f"{name:<8} open={code.count(a):<5} close={code.count(b):<5} balanced={ok}")

used = set(re.findall(r"Icons\.Default\.(\w+)", s))
imported = set(re.findall(r"import androidx\.compose\.material\.icons\.filled\.(\w+)", s))
print("MISSING icons =", sorted(used - imported))
print("unused icons  =", sorted(imported - used))

for name in [
    "AliasesScreen", "MailboxManageScreen", "MailboxManageRow",
    "CurrentMailboxHint", "DmNavItem", "MailboxSheetRow", "MailboxSheet",
]:
    print(f"  {name:<24} refs={s.count(name)}")

for name in [
    "BackHandler", "AlertDialog", "ModalBottomSheet", "DropdownMenu",
    "DropdownMenuItem", "NavigationBarItem", "ImageVector", "DmPalette",
]:
    imported_ok = (
        ("import androidx.compose.material3." + name in s)
        or ("import androidx.activity.compose." + name in s)
        or ("import androidx.compose.ui.graphics.vector." + name in s)
        or ("import com.ifwqu.domainmail.ui." + name in s)
        or name in {"DmPalette"}  # 同包定义，无需 import
    )
    print(f"  {name:<24} available={str(imported_ok):<5} refs={s.count(name)}")

print("lines =", len(s.splitlines()))
