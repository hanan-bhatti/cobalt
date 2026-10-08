import re

with open('app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt', 'r') as f:
    content = f.read()

# Replace the Box with HorizontalPager
old_box_start = """            var totalDrag by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { _ -> totalDrag = 0f },
                            onDragEnd = { 
                                val currentIndex = SavedItemType.entries.indexOf(item.type)
                                if (totalDrag > 100f && currentIndex > 0) {
                                    viewModel.switchActiveCaptureType(SavedItemType.entries[currentIndex - 1])
                                } else if (totalDrag < -100f && currentIndex < SavedItemType.entries.size - 1) {
                                    viewModel.switchActiveCaptureType(SavedItemType.entries[currentIndex + 1])
                                }
                                totalDrag = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            }
                        )
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {"""

new_pager_start = """            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = SavedItemType.entries.indexOf(item.type).coerceAtLeast(0),
                pageCount = { SavedItemType.entries.size }
            )

            // Keep the item type selector synced with the pager
            androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                if (SavedItemType.entries[pagerState.currentPage] != item.type) {
                    viewModel.switchActiveCaptureType(SavedItemType.entries[pagerState.currentPage])
                }
            }
            androidx.compose.runtime.LaunchedEffect(item.type) {
                val index = SavedItemType.entries.indexOf(item.type)
                if (index >= 0 && index != pagerState.currentPage) {
                    pagerState.animateScrollToPage(index)
                }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = true,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val pageType = SavedItemType.entries[pageIndex]
                Column(modifier = Modifier.fillMaxWidth()) {"""

content = content.replace(old_box_start, new_pager_start)

# We need to rename item.type to pageType inside the Column, from "val pageType..." up to ORGANIZATIONAL FOLDERS SELECTION
# Let's find the start and end indices of the block to replace
start_idx = content.find("val pageType = SavedItemType.entries[pageIndex]")
end_idx = content.find("// ORGANIZATIONAL FOLDERS SELECTION")

if start_idx != -1 and end_idx != -1:
    block = content[start_idx:end_idx]
    
    # We replace `item.type` with `pageType` inside this block
    # But carefully avoid replacing things that shouldn't be replaced.
    # `item.type` is fine to blindly replace with `pageType` here.
    block = block.replace("item.type", "pageType")
    
    content = content[:start_idx] + block + content[end_idx:]

with open('app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt', 'w') as f:
    f.write(content)
print("Done")
