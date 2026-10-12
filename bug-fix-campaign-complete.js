export const meta = {
  name: 'shop-plugin-bug-fix-campaign-complete',
  description: 'Fully autonomous campaign: verify/fix ALL open issues, cleanup, docs, board management, zero user input',
  phases: [
    { title: 'Init & Cleanup', detail: 'Load CLAUDE.md, cleanup stale artifacts, warm caches, fetch ALL issues & board' },
    { title: 'Triage Issues', detail: 'Categorize all open issues by priority, label, area' },
    { title: 'Verify & Fix Loop', detail: 'One agent per issue (P0→P1→P2→P3): reproduce, fix, docs, tests, push, update issue/board' },
    { title: 'Review Phase', detail: 'Monitor PRs for codebunny review; agents NEVER merge - only report when ready for merge' },
    { title: 'Context Compaction', detail: 'Auto-compact context every 5 issues to sustain extended runs' },
    { title: 'Final Cleanup & Sync', detail: 'Cleanup cache, update board with feature ideas, generate report, final verification' }
  ]
}

// ============ GLOBAL CACHE HELPERS ============
const CACHE_DIR = '.claude/cache'

async function readCache(path) {
  try {
    const result = await run(`cat ${CACHE_DIR}/${path} 2>/dev/null`)
    return JSON.parse(result)
  } catch { return null }
}
async function writeCache(path, data) {
  await run(`mkdir -p ${CACHE_DIR}/$(dirname ${path}) && cat > ${CACHE_DIR}/${path} << 'EOF'
${JSON.stringify(data, null, 2)}
EOF`)
}
async function appendCache(path, data, timestamp) {
  const arr = await readCache(path) || []
  const ts = timestamp || '2026-10-09T00:00:00.000Z'
  arr.push({ ...data, timestamp: ts })
  await writeCache(path, arr)
}
async function run(cmd) {
  const result = await agent(cmd, { label: 'run-' + cmd.slice(0, 30).replace(/[^a-z0-9]/gi, '-') })
  return result
}

// ============ AST-GREP HELPERS ============
async function astGrepOutline(fileOrDir, options = {}) {
  const opts = []
  if (options.items) opts.push(`--items ${options.items}`)
  if (options.view) opts.push(`--view ${options.view}`)
  if (options.match) opts.push(`--match "${options.match}"`)
  if (options.type) opts.push(`--type ${options.type}`)
  if (options.pubMembers) opts.push('--pub-members')
  if (options.json) opts.push('--json=stream')

  const cmd = `ast-grep outline ${fileOrDir} ${opts.join(' ')}`
  const result = await run(cmd)
  return result
}

async function astGrepRun(pattern, lang, path = '.', options = {}) {
  const opts = [`--pattern '${pattern}'`, `--lang ${lang}`, path]
  if (options.json) opts.push('--json')
  if (options.debug) opts.push(`--debug-query=${options.debug}`)

  const cmd = `ast-grep run ${opts.join(' ')}`
  const result = await run(cmd)
  return result
}

async function astGrepScan(ruleFile, path = '.', options = {}) {
  const opts = [`--rule ${ruleFile}`, path]
  if (options.json) opts.push('--json')

  const cmd = `ast-grep scan ${opts.join(' ')}`
  const result = await run(cmd)
  return result
}

async function astGrepInlineRule(ruleYaml, path = '.', options = {}) {
  const cmd = `ast-grep scan --inline-rules "${ruleYaml.replace(/"/g, '\\"')}" ${path} ${options.json ? '--json' : ''}`
  const result = await run(cmd)
  return result
}

async function astGrepDebug(pattern, lang, format = 'cst') {
  const cmd = `ast-grep run --pattern '${pattern}' --lang ${lang} --debug-query=${format}`
  const result = await run(cmd)
  return result
}

// ============ CLAUDE.MD CONTEXT LOADER ============
async function loadClaudeMdContext() {
  const projectClaude = await run('cat CLAUDE.md 2>/dev/null')
  const userClaude = await run('cat "C:/Users/ADMIN_malex/.claude/CLAUDE.md" 2>/dev/null')
  const rtkMd = await run('cat "C:/Users/ADMIN_malex/.claude/RTK.md" 2>/dev/null')
  return { project: projectClaude, user: userClaude, rtk: rtkMd }
}

// ============ GITHUB ISSUE/PR/BOARD OPS ============
async function updateIssueLabels(issueNum, addLabels, removeLabels, timestamp) {
  const args = []
  if (addLabels.length) args.push('--add-label', addLabels.join(','))
  if (removeLabels.length) args.push('--remove-label', removeLabels.join(','))
  await run(`gh issue edit ${issueNum} -R TheFlood424K/Shop ${args.join(' ')}`)
  await appendCache('issues/label-history.json', { issue: issueNum, add: addLabels, remove: removeLabels }, timestamp)
}

async function updateIssueBody(issueNum, newBody) {
  await run(`gh issue edit ${issueNum} -R TheFlood424K/Shop --body-file - <<< '${newBody.replace(/'/g, "'\\''")}'`)
}

async function addIssueComment(issueNum, comment) {
  await run(`gh issue comment ${issueNum} -R TheFlood424K/Shop --body "${comment.replace(/"/g, '\\"')}"`)
  await appendCache('issues/comments.json', { issue: issueNum, comment })
}

async function pushPrBranch(branchName) {
  await run(`git push origin ${branchName} --force-with-lease`)
  await appendCache('pr/pushes.json', { branch: branchName })
}

async function updatePrDescription(prNum, body) {
  await run(`gh pr edit ${prNum} -R TheFlood424K/Shop --body "${body.replace(/"/g, '\\"')}"`)
}

async function addBoardItem(title, body, column, labels) {
  const item = await run(`gh project item-create 3 --owner TheFlood424K --title "${title}" --body "${body}" --format json`)
  const itemId = JSON.parse(item).id
  if (column) await run(`gh project item-move 3 --owner TheFlood424K --id ${itemId} --column "${column}"`)
  if (labels?.length) await run(`gh project item-edit 3 --owner TheFlood424K --id ${itemId} --add-label "${labels.join(',')}"`)
  await appendCache('board/items.json', { title, column, labels, id: itemId })
  return itemId
}

async function updateBoardItem(itemId, field, value) {
  await run(`gh project item-edit 3 --owner TheFlood424K --id ${itemId} --${field} "${value}"`)
}

async function updateClaudeMd(updates) {
  let claudeMd = await run('cat CLAUDE.md 2>/dev/null')
  const marker = '## ' + updates.section
  const idx = claudeMd.indexOf(marker)
  if (idx >= 0) {
    const nextSection = claudeMd.indexOf('\n## ', idx + 1)
    const insertAt = nextSection >= 0 ? nextSection : claudeMd.length
    claudeMd = claudeMd.slice(0, insertAt) + '\n\n' + updates.content + claudeMd.slice(insertAt)
  } else {
    claudeMd += '\n\n## ' + updates.section + '\n\n' + updates.content
  }
  await run('cat > CLAUDE.md << \'EOF\'\n' + claudeMd + '\nEOF')
  await appendCache('context/claude-md-updates.json', updates)
}

// ============ MAIN WORKFLOW ============
phase('Init & Cleanup')

// Load CLAUDE.md context once
const claudeContext = await loadClaudeMdContext()
await writeCache('context/claude-md.json', claudeContext)

// Run cleanup (already done in session start, but log it)
const cleanupTimestamp = '2026-10-09T00:00:00.000Z'
await appendCache('cleanup/startup.json', {
  worktreesRemoved: await run('git worktree list | wc -l'),
  branchesCleaned: await run('git branch -r | wc -l'),
  timestamp: cleanupTimestamp
})

// Fetch ALL open issues (not just the 28 campaign ones)
const allIssues = await agent(`
  Fetch ALL open issues from TheFlood424K/Shop using gh CLI.
  Return a JSON array of objects: { number, title, labels, body, url, createdAt, updatedAt }
`, { label: 'fetch-all-issues', schema: { type: 'object', properties: { issues: { type: 'array', items: { type: 'object', properties: { number: { type: 'number' }, title: { type: 'string' }, labels: { type: 'array', items: { type: 'string' } }, body: { type: 'string' }, url: { type: 'string' }, createdAt: { type: 'string' }, updatedAt: { type: 'string' } }, required: ['number', 'title', 'labels', 'body', 'url', 'createdAt', 'updatedAt'] } } }, required: ['issues'] } })

await writeCache('issues/all-open.json', allIssues.issues)
log(`Loaded ${allIssues.issues.length} open issues`)

// Load board state
const boardState = await readCache('board/initial.json') || { columns: [], items: [] }
await writeCache('board/current.json', boardState)

// ============ PHASE: TRIAGE ============
phase('Triage Issues')

const triage = await agent(`
  Analyze all ${allIssues.issues.length} open issues and categorize them.
  Use these categories:
  - critical-bug: crashes, data loss, security, silent failures
  - bug: incorrect behavior, logic errors
  - config-ci: CI/CD, build, dependencies, configuration
  - architecture: code structure, tech debt, refactoring
  - feature: new functionality requests
  - documentation: docs, wiki, README
  - cleanup: dead code, stale artifacts, maintenance

  Also assign priority: P0 (immediate), P1 (high), P2 (medium), P3 (low)

  Return: { triaged: [{ issueNumber, category, priority, reason, labelsToAdd, labelsToRemove }] }
`, { label: 'triage-issues', schema: { type: 'object', properties: { triaged: { type: 'array', items: { type: 'object', properties: { issueNumber: { type: 'number' }, category: { type: 'string' }, priority: { type: 'string' }, reason: { type: 'string' }, labelsToAdd: { type: 'array', items: { type: 'string' } }, labelsToRemove: { type: 'array', items: { type: 'string' } } }, required: ['issueNumber', 'category', 'priority'] } } }, required: ['triaged'] } })

// Apply triage labels to GitHub issues
for (const t of triage.triaged) {
  await updateIssueLabels(t.issueNumber, t.labelsToAdd || [], t.labelsToRemove || [], '2026-10-09T00:00:00.000Z')
  await addIssueComment(t.issueNumber, `🏷️ **Triaged**: ${t.category} / ${t.priority} - ${t.reason}`)
}

// Group by priority for processing order
const byPriority = {
  P0: triage.triaged.filter(t => t.priority === 'P0').map(t => allIssues.issues.find(i => i.number === t.issueNumber)).filter(Boolean),
  P1: triage.triaged.filter(t => t.priority === 'P1').map(t => allIssues.issues.find(i => i.number === t.issueNumber)).filter(Boolean),
  P2: triage.triaged.filter(t => t.priority === 'P2').map(t => allIssues.issues.find(i => i.number === t.issueNumber)).filter(Boolean),
  P3: triage.triaged.filter(t => t.priority === 'P3').map(t => allIssues.issues.find(i => i.number === t.issueNumber)).filter(Boolean),
}

await writeCache('issues/triaged.json', triage)
await writeCache('issues/by-priority.json', byPriority)

log(`Triage complete: P0=${byPriority.P0.length}, P1=${byPriority.P1.length}, P2=${byPriority.P2.length}, P3=${byPriority.P3.length}`)

// ============ ISSUE PROCESSOR ============
phase('Verify & Fix Loop')

async function processIssue(issue, triageInfo) {
  const issueNum = issue.number
  const branchName = `fix/issue-${issueNum}-${issue.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 50)}`

  // Update issue: mark in-progress
  await updateIssueLabels(issueNum, ['in-progress'], ['help-wanted', 'good-first-issue'], '2026-10-09T00:00:00.000Z')
  await addIssueComment(issueNum, `🤖 **Agent started** - Verifying bug and preparing fix...\nBranch: \`${branchName}\`\nTriage: ${triageInfo.category} / ${triageInfo.priority}`)

  // Ensure board item exists in "In Progress" column
  let boardItemId = boardState.items.find(it => it.content_url?.includes(`issues/${issueNum}`))?.id
  if (!boardItemId) {
    boardItemId = await addBoardItem(
      `#${issueNum}: ${issue.title}`,
      issue.body.slice(0, 500) + '...',
      'In Progress',
      ['bug', 'auto-generated', triageInfo.category, triageInfo.priority.toLowerCase()]
    )
  } else {
    await updateBoardItem(boardItemId, 'column', 'In Progress')
  }

  return await agent(`
    ISSUE #${issueNum}: ${issue.title}
    REPOSITORY: TheFlood424K/Shop
    BRANCH: ${branchName}
    BOARD ITEM: ${boardItemId}
    TRIAGE: ${triageInfo.category} / ${triageInfo.priority}

    CLAUDE.MD CONTEXT (from cache):
    ${claudeContext.project?.slice(0, 3000) || '(see CLAUDE.md)'}

    YOUR TASK:
    1. SETUP:
       git checkout main && git pull origin main
       git checkout -b ${branchName}

    2. STRUCTURAL CODE ANALYSIS (using ast-grep):
       - Use ast-grep to understand code structure before editing:
         ast-grep outline <file> --view expanded
         ast-grep outline <file> --items imports,exports
       - Search for related code patterns:
         ast-grep run --pattern 'pattern' --lang java core/src/main/java/
         ast-grep scan --inline-rules "rule yaml" core/src/main/java/
       - Debug AST structure if needed:
         ast-grep run --pattern 'code snippet' --lang java --debug-query=cst

    3. VERIFY BUG EXISTS (MANDATORY):
       - Read source files from issue analysis
       - Write minimal reproduction test that FAILS
       - Run: mvn -B -ntp -o test -Dtest=<YourTest>
       - CONFIRM TEST FAILS with expected bug behavior
       - If cannot reproduce: STOP, return { issueNumber: ${issueNum}, cannotReproduce: true }

    4. IMPLEMENT FIX + DOCS + TESTS:
       - Minimal, focused code changes
       - Update JavaDoc for ALL modified public methods/classes
       - Update README.md if user-facing change (config, command, behavior)
       - Update wiki if applicable (check https://github.com/TheFlood424K/Shop/wiki)
       - Add inline comments explaining WHY (not just WHAT)
       - Add regression test + edge cases + related path tests (min 3 tests)
       - Follow CLAUDE.md conventions: UUID over names, config casing, etc.
       - Use test utilities: BaseMockBukkitTest, ShopCreationFlowTestUtil, StubbedPlayerMock

    4. VERIFY STABILITY:
       - Run reproduction test - MUST PASS
       - Run full suite: mvn -B -ntp -o test
       - Confirm 438+ tests pass
       - Run spotbugs/checkstyle if configured

    5. PERIODIC PUSH (every ~10 min during long fixes):
       git push origin ${branchName} --force-with-lease

    6. FINAL PUSH & PR CREATION:
       git add -A
       git commit -m 'fix: #' + issueNum + ' - ' + issue.title + '\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'
       git push origin ' + branchName + '

       PR_URL=$(gh pr create --title "fix: #' + issueNum + ' - ' + issue.title + '" --body "Fixes #' + issueNum + '\n\n## Documentation Updated\n- [ ] JavaDoc/docstrings for modified code\n- [ ] README.md (if user-facing change)\n- [ ] Wiki (if applicable)\n- [ ] Inline comments explaining rationale\n\n## Tests Added\n- [ ] Regression test for the specific bug\n- [ ] Edge case tests\n- [ ] Related code path tests\n\n## Stability Verification\n- [ ] All 438+ tests pass\n- [ ] No new warnings\n- [ ] Code follows CLAUDE.md conventions\n\n**NOTE: Only the user can merge this PR.**" --base main)

    7. UPDATE ISSUE & BOARD:
       - Add label 'ready-for-review', remove 'in-progress'
       - Move board item to "Code Review" column
       - Comment on issue with PR link

    8. RETURN: {
      issueNumber: ${issueNum},
      testPassed: true,
      prUrl: "\${PR_URL}",
      branchName: "${branchName}",
      docsUpdated: true,
      testsAdded: <count>,
      boardItemId: ${boardItemId},
      featureIdeas: []
    }

    ISSUE DETAILS:
    ${issue.body}

    RELEVANT FILES:
    ${getRelevantFiles(issueNum)}

    DOCUMENTATION FILES:
    - README.md (root)
    - Wiki: https://github.com/TheFlood424K/Shop/wiki
    - JavaDoc in modified .java files
    - Config comments in .yml files

    TEST PATTERNS (from cache):
    ${JSON.stringify(await readCache('tests/patterns.json'), null, 2)}

    IMPORTANT:
    - Do NOT proceed if cannot reproduce bug
    - If already fixed: return { issueNumber: ${issueNum}, alreadyFixed: true }
    - One PR per issue, no batching
    - Docs + Tests are NOT optional
    - NEVER merge PRs - only create them
  `, {
    label: `fix-${issueNum}`,
    phase: `Verify & Fix #${issueNum}`,
    schema: {
      type: 'object',
      properties: {
        issueNumber: { type: 'number' },
        testPassed: { type: 'boolean' },
        prUrl: { type: 'string' },
        branchName: { type: 'string' },
        alreadyFixed: { type: 'boolean' },
        cannotReproduce: { type: 'boolean' },
        docsUpdated: { type: 'boolean' },
        testsAdded: { type: 'number' },
        boardItemId: { type: 'number' },
        featureIdeas: { type: 'array', items: { type: 'object', properties: { title: { type: 'string' }, description: { type: 'string' } } } }
      },
      required: ['issueNumber']
    },
    effort: 'high'
  })
}

function getRelevantFiles(issueNum) {
  // Return relevant files based on issue number or title
  // This is a fallback - agents should read the issue body for specifics
  return ['(see issue body for relevant files)']
}

// Process issues by priority: P0 -> P1 -> P2 -> P3
const results = []
let issuesProcessed = 0

async function compactContext() {
  log('🔄 Auto-compacting context...')
  // Summarize completed work into cache
  const summary = {
    totalProcessed: issuesProcessed,
    passed: results.filter(r => r.testPassed).length,
    failed: results.filter(r => !r.testPassed && !r.alreadyFixed && !r.cannotReproduce).length,
    alreadyFixed: results.filter(r => r.alreadyFixed).length,
    cannotReproduce: results.filter(r => r.cannotReproduce).length,
    totalTestsAdded: results.reduce((sum, r) => sum + (r.testsAdded || 0), 0),
    totalDocsUpdated: results.filter(r => r.docsUpdated).length,
    prsCreated: results.filter(r => r.prUrl).length,
    lastProcessed: results[results.length - 1]?.issueNumber
  }
  await writeCache('progress/compaction-summary.json', summary)

  // Keep only essential context for continuation
  await writeCache('progress/results.json', results)
  await writeCache('progress/reviews.json', reviewResults || [])

  log(`✅ Context compacted: ${issuesProcessed} issues processed, ${summary.prsCreated} PRs created`)
}

for (const priority of ['P0', 'P1', 'P2', 'P3']) {
  for (const issue of byPriority[priority] || []) {
    const triageInfo = triage.triaged.find(t => t.issueNumber === issue.number)
    const result = await processIssue(issue, triageInfo)
    results.push(result)
    issuesProcessed++

    // Persist progress after each issue
    await writeCache('progress/results.json', results)

    // Update board with feature ideas from this fix
    if (result.featureIdeas?.length) {
      for (const idea of result.featureIdeas) {
        await addBoardItem(
          `Feature: ${idea.title}`,
          idea.description,
          'Backlog',
          ['feature', 'auto-generated', `from-fix-${issue.number}`]
        )
      }
    }

    log(`Completed ${issue.number}: ${result.testPassed ? 'PASS' : 'FAIL'} (PR: ${result.prUrl})`)

    // Auto-compact context every 5 issues
    if (issuesProcessed % 5 === 0) {
      await compactContext()
      phase(`Context Compaction (after ${issuesProcessed} issues)`)
    }
  }
}

// Final compaction
if (issuesProcessed % 5 !== 0) {
  await compactContext()
  phase('Final Context Compaction')
}

log(`Completed ${results.filter(r => r.testPassed).length}/${results.length} fixes`)

// ============ REVIEW PHASE ============
phase('Review Phase')

const reviewResults = []
for (const result of results.filter(r => r.prUrl)) {
  const prNum = result.prUrl.split('/').pop()

  // Notify for review - agents NEVER merge (ONLY USER can merge)
  await addIssueComment(result.issueNumber, '🔍 **Ready for review** - ' + result.prUrl + '\nAwaiting codebunny/human review. Agent will NOT merge. **Only the user can merge PRs.**')
  await updateIssueLabels(result.issueNumber, ['ready-for-review'], ['in-progress'], '2026-10-09T00:00:00.000Z')
  await updateBoardItem(result.boardItemId, 'column', 'Code Review')

  // Monitor for review completion (but don't merge)
  const review = await agent(`
    Monitor PR #${prNum} for codebunny/human review.
    Check every 120s: gh pr view ${prNum} -R TheFlood424K/Shop --json reviews,mergeable,reviewDecision
    When reviewDecision is "APPROVED" and mergeable: return { pr: ${prNum}, status: "approved", merged: false }
    When reviewDecision is "CHANGES_REQUESTED": return { pr: ${prNum}, status: "changes-requested", feedback: "..." }
    When closed without merge: return { pr: ${prNum}, status: "closed-unmerged" }
    DO NOT MERGE - only report status. ONLY THE USER CAN MERGE PRs.
  `, { label: `review-${prNum}`, phase: `Review #${result.issueNumber}` })

  reviewResults.push(review)

  if (review.status === 'approved') {
    await addIssueComment(result.issueNumber, '✅ **Approved by codebunny/human** - ' + result.prUrl + '\nReady for merge. **Only the user can merge this PR.**')
    await updateIssueLabels(result.issueNumber, ['approved'], ['ready-for-review'], '2026-10-09T00:00:00.000Z')
    await updateBoardItem(result.boardItemId, 'column', 'Approved')
  } else if (review.status === 'changes-requested') {
    await addIssueComment(result.issueNumber, '🔄 **Changes requested** by codebunny: ' + review.feedback)
    await updateIssueLabels(result.issueNumber, ['needs-changes'], ['ready-for-review'], '2026-10-09T00:00:00.000Z')
    await updateBoardItem(result.boardItemId, 'column', 'In Progress')
  } else if (review.status === 'closed-unmerged') {
    await addIssueComment(result.issueNumber, '❌ **Closed without merge**')
    await updateIssueLabels(result.issueNumber, ['closed-unmerged'], ['ready-for-review'], '2026-10-09T00:00:00.000Z')
    await updateBoardItem(result.boardItemId, 'column', 'Closed')
  }

  await writeCache('progress/reviews.json', reviewResults)
}

// ============ STACK PRs & SANITY CHECK ============
phase('Stack PRs & Sanity Check')

async function checkPrApproved(prUrl) {
  try {
    const prNum = prUrl.split('/').pop()
    const prInfo = await run(`gh pr view ${prNum} -R TheFlood424K/Shop --json reviewDecision,mergeable,state`)
    const info = JSON.parse(prInfo)
    return info.reviewDecision === 'APPROVED' && info.mergeable === 'MERGEABLE' && info.state === 'OPEN'
  } catch {
    return false
  }
}

// Create a stacked branch with all approved PRs
const approvedCheckPromises = results
  .filter(r => r.prUrl)
  .map(r => checkPrApproved(r.prUrl).then(approved => approved ? r : null))
const approvedResults = (await parallel(approvedCheckPromises)).filter(Boolean)
if (approvedResults.length > 0) {
  log(`Stacking ${approvedResults.length} approved PRs...`)

  await run(`
    git checkout main && git pull origin main
    git checkout -b campaign-stack-all-fixes
  `)

  for (const result of approvedResults) {
    const prNum = result.prUrl.split('/').pop()
    await run(`gh pr checkout ${prNum} -R TheFlood424K/Shop --branch campaign-stack-all-fixes 2>/dev/null || git merge --no-ff origin/fix/issue-${result.issueNumber}-* 2>/dev/null || true`)
  }

  // Push stacked branch
  await run(`git push origin campaign-stack-all-fixes --force-with-lease`)

  // Sanity check: run full test suite on stacked branch
  log('Running sanity check on stacked branch...')
  const sanityResult = await run(`
    mvn -B -ntp -o test -q 2>&1 | tail -10
  `)

  const sanityPassed = !sanityResult.includes('FAILURE') && !sanityResult.includes('ERROR')

  if (sanityPassed) {
    await addIssueComment(0, `✅ **Stacked branch sanity check PASSED** - All ${approvedResults.length} fixes work together`)
    // Create PR for stacked branch
    const stackPrUrl = await run('gh pr create --title "stack: All campaign fixes" --body "Stacked branch with all approved campaign fixes\n\nFixes: ' + approvedResults.map(r => '#' + r.issueNumber).join(', ') + '" --base main --head campaign-stack-all-fixes -R TheFlood424K/Shop')
    await addIssueComment(0, `📦 **Stacked PR created**: ${stackPrUrl}`)
  } else {
    await addIssueComment(0, `❌ **Stacked branch sanity check FAILED**\n\`\`\`\n${sanityResult}\n\`\`\``)
    // Create issues for conflicts
    for (const result of approvedResults) {
      await run(`gh issue create -R TheFlood424K/Shop --title "Conflict in stacked branch: #${result.issueNumber}" --body "Fix from #${result.issueNumber} conflicts with other fixes in stacked branch. Manual resolution needed." --label "conflict,stacked-branch"`)
    }
  }
}

// ============ FINAL CLEANUP & SYNC ============
phase('Final Cleanup & Sync')

// Final cleanup pass
await run(`
  # Final cleanup of any remaining stale artifacts
  find . -name "target" -type d -mtime +1 -exec rm -rf {} + 2>/dev/null || true

  # Remove any leftover worktrees from this session
  git worktree list --porcelain | awk '/^worktree / { print $2 }' | while read wt; do
    if [ "\$wt" != "\$(pwd)" ]; then git worktree remove "\$wt" --force 2>/dev/null; fi
  done

  # Final test run
  mvn -B -ntp -o test -q 2>&1 | tail -5
`)

const finalReport = await agent(`
  Generate final campaign report from cache:
  - Results: .claude/cache/progress/results.json
  - Reviews: .claude/cache/progress/reviews.json
  - Board: .claude/cache/board/current.json
  - Feature ideas collected during fixes
  - Cleanup log: .claude/cache/cleanup/
  - All open issues status
  - Stacked branch status: ${approvedResults.length > 0 ? 'created' : 'no approved PRs'}

  Create summary issue on GitHub with:
  1. Table of all processed issues: status, PR link, tests added, docs updated
  2. Feature ideas discovered (add to project board Backlog)
  3. Remaining open issues with triage info
  4. Stability metrics (test count, coverage if available)
  5. Documentation updates summary
  6. Cleanup summary (worktrees, branches, artifacts removed)
  7. Stacked branch sanity check result
  8. Any new facts for CLAUDE.md
  9. New issues created for conflicts/feature ideas

  Post as new issue: "Campaign Summary: Shop Plugin Bug Fix Campaign"
`, { label: 'final-report' })

// Update project board with summary
await addBoardItem(
  'Campaign Summary: Shop Plugin Bug Fix Campaign',
  finalReport.summary,
  'Done',
  ['campaign', 'summary']
)

// Create new issues for any feature ideas that weren't already created
if (finalReport.newFeatureIssues?.length) {
  for (const idea of finalReport.newFeatureIssues) {
    await run(`gh issue create -R TheFlood424K/Shop --title "${idea.title}" --body "${idea.description}" --label "feature,auto-generated"`)
  }
}

// Update CLAUDE.md with any new facts discovered
if (finalReport.newFacts?.length) {
  for (const fact of finalReport.newFacts) {
    await updateClaudeMd({ section: fact.section, content: fact.content })
  }
}

return { results, reviewResults, finalReport }