with open('app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt', 'r') as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    if i >= 281: # line 282
        depth += line.count('{')
        depth -= line.count('}')
        if depth == 0:
            print(f"Column closes at line {i+1}")
            break
