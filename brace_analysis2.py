filepath = r"C:\Users\malex\Shop\.mcc-claude\worktrees\wt-subissue-341\core\src\main\java\com\snowgears\shop\util\ShopMessage.java"
with open(filepath, "r", encoding="utf-8", errors="replace") as f:
    content = f.read()

# First, remove /* ... */ multi-line comments
import re
content_no_comments = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

# Now split into lines
lines = content_no_comments.splitlines()

brace_count = 0
for i, line in enumerate(lines, 1):
    line_stripped = line

    # Handle // comments
    comment_pos = line_stripped.find("//")
    if comment_pos != -1:
        before = line_stripped[:comment_pos]
        quote_count = before.count('"') + before.count("'")
        if quote_count % 2 == 0:
            line_stripped = line_stripped[:comment_pos]

    # Also remove any remaining */ from incomplete comment stripping
    line_stripped = line_stripped.replace("*/", "")

    open_count = line_stripped.count("{")
    close_count = line_stripped.count("}")
    brace_count += open_count - close_count

    if brace_count < 0:
        print(f"Line {i}: brace_count went NEGATIVE to {brace_count}")
        print(f"  Line: {line[:120]}...")
        break
else:
    print(f"No line caused brace_count to go negative")
    print(f"Final brace_count after comment stripping: {brace_count}")

# Now count raw braces in the original file
with open(filepath, "r", encoding="utf-8", errors="replace") as f:
    raw_lines = f.readlines()

raw_open = sum(line.count("{") for line in raw_lines)
raw_close = sum(line.count("}") for line in raw_lines)
print(f"\nRaw counts: open={raw_open}, close={raw_close}, difference={raw_open - raw_close}")