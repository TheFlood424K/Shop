filepath = r"C:\Users\malex\Shop\.mcc-claude\worktrees\wt-subissue-341\core\src\main\java\com\snowgears\shop\util\ShopMessage.java"
with open(filepath, "r", encoding="utf-8", errors="replace") as f:
    lines = f.readlines()

brace_count = 0
for i, line in enumerate(lines, 1):
    line_stripped = line

    # Handle // comments - check if // is inside a string
    comment_pos = line_stripped.find("//")
    if comment_pos != -1:
        before = line_stripped[:comment_pos]
        quote_count = before.count('"') + before.count("'")
        if quote_count % 2 == 0:  # Even number of quotes means // is not inside a string
            line_stripped = line_stripped[:comment_pos]

    open_count = line_stripped.count("{")
    close_count = line_stripped.count("}")
    brace_count += open_count - close_count

    if brace_count < 0:
        print(f"Line {i}: brace_count went NEGATIVE to {brace_count}")
        print(f"  Line: {line.rstrip()[:120]}...")
        break
else:
    print(f"No line caused brace_count to go negative")
    print(f"Final brace_count: {brace_count}")