# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

This is a **multi-agent orchestration repository** that coordinates development across three separate repositories using a shared devcontainer environment. The architecture diagram is documented in `agent-flow.excalidraw`.

### Multi-Repository Architecture

The mono repo has one folder for backend and one for frontend. It also has one repo (git worktree branch) per responsibility of agent:
- **dev branch** (whos-hot-nhl repo) - Master coordination and shared configuration
- **dev-backend branch** - Backend agent. Only works in this branch and the /root/backend folder.
- **dev-frontend branch** - Frontend agent. Only works in this branch and the /root/frontend folder.

All three branches/repositories share:
- The same devcontainer environment
- Firewall rules and network restrictions
- Development tools and configurations
- Each maintains its own CLAUDE.md file for repo-specific guidance
- Frontend agent (frontend repo) should use CLAUDE.md file found in /frontend/CLAUDE.md
- Frontend agent (frontend repo) should only add, edit or delete files in /frontend directory
- Backend agent should use CLAUDE.md file found in /backend/CLAUDE.md
- Backend agent (backend repo) should only add, edit or delete files in /backend directory

## Devcontainer Environment

### Installed Tools

**Languages & Runtimes:**
- Node.js 20
- Java 25 (Temurin JDK)
- Maven 3.9.9

**Development Tools:**
- git, gh (GitHub CLI)
- fzf (fuzzy finder)
- delta (enhanced git diff)
- zsh with powerlevel10k theme

**Network Tools:**
- iptables, ipset, iproute2
- dnsutils, aggregate

### Port Forwarding

- Port 3000: Frontend application (auto-forwarded with notification)

## Network Security

### Firewall Configuration

The devcontainer runs a **strict firewall** (`init-firewall.sh`) that:
- **Blocks all outbound traffic by default**
- Only allows specific domains:
  - GitHub (web, api, git)
  - npm registry
  - Anthropic API
  - Sentry, Statsig
  - VS Code marketplace
  - Maven repositories
  - Adoptium JDK packages
  - Apache archives

**Testing firewall rules:**
```bash
# Should fail (blocked)
curl https://example.com

# Should succeed (allowed)
curl https://api.github.com/zen
```

## Multi-Agent Workflow

### Repository Structure

```
/workspace/
├── whos-hot-nhl/          (this repo - coordination)
├── backend/               (dev-backend repo)
└── frontend/              (dev-frontend repo)
```

### Development Pattern

Each agent (backend, frontend) maintains its own:
- CLAUDE.md with repo-specific guidance
- Git history and branches using Git Worktrees
- Build and test configurations

### Best Practices

- **Use only this CLAUDE.md file** for this branch
- **Always check both branches** before making documentation changes
- **Pull frontend changes** when working on backend (and vice versa)
- **Commit frequently** to minimize merge conflicts
- **Communicate changes** that affect both frontend and backend
- **Test after merging** to ensure nothing breaks

### Worktree Commands Reference

```bash
# List all worktrees
git worktree list

# Fetch all branches
git fetch origin

# Merge changes from another branch
git merge origin/<branch-name> --no-edit

# Check current branch
git branch --show-current

# View remote branches
git branch -r
```

**Coordination workflow:**
1. Plan changes in the master coordination repo
2. Always check for changes in both backend and frontend repo before continuing planing and merging
3. Commit and push frequently to avoid merge conflicts
4. Each repo can pull from master branch to check for changes that affect each branch
5. Only master branch can pull from all branches
6. Only master branch can merge changes from other (backend and frontend) branches

### Git Configuration

**Remote repository**
https://github.com/Kyozoku-des/whos-hot-nhl.git

Each repository has its own git configuration. When working across repos:
```bash
# Check which repo you're in
pwd

# Each repo syncs independently
cd /workspace/dev-backend && git pull
cd /workspace/dev-frontend && git pull
cd /workspace/whos-hot-nhl && git pull
```

**IMPORTANT**
Never touch master branch.

## VS Code Configuration

### Extensions

Pre-installed:
- Claude Code (anthropic.claude-code)
- ESLint (dbaeumer.vscode-eslint)
- Prettier (esbenp.prettier-vscode)
- GitLens (eamodio.gitlens)

### Settings

- Format on save: Enabled (Prettier)
- Auto-fix ESLint on save: Enabled
- Default shell: zsh
- Default editor: nano

## Environment Variables

- `DEVCONTAINER=true` - Indicates running in devcontainer
- `NODE_OPTIONS=--max-old-space-size=4096` - Increased memory for Node
- `CLAUDE_CONFIG_DIR=/home/node/.claude` - Claude Code config directory
- `JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64`
- `MAVEN_HOME=/opt/maven`

## Persistent Storage

The following are mounted as Docker volumes for persistence across rebuilds:
- `/commandhistory` - Bash/zsh history
- `/home/node/.claude` - Claude Code configuration

## Architecture Reference

The `agent-flow.excalidraw` diagram shows:
- **Dev branch** (red) - Coordination repo with planning phase
- **Backend branch** (blue) - Backend development
- **Frontend branch** (green) - Frontend development
- All branches sync with GitHub
- Each branch has its own CLAUDE.md file

## Important Notes

- The container runs as the `node` user (non-root)
- Maximum Node.js memory is set to 4GB
- GitStatus is disabled in powerlevel10k for performance
