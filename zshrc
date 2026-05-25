# If you come from bash you might have to change your $PATH.
# export PATH=$HOME/bin:$HOME/.local/bin:/usr/local/bin:$PATH

# Path to your Oh My Zsh installation.
export ZSH="$HOME/.oh-my-zsh"

# Set name of the theme to load --- if set to "random", it will
# load a random theme each time Oh My Zsh is loaded, in which case,
# to know which specific one was loaded, run: echo $RANDOM_THEME
# See https://github.com/ohmyzsh/ohmyzsh/wiki/Themes
ZSH_THEME="agnoster"

# Set list of themes to pick from when loading at random
# Setting this variable when ZSH_THEME=random will cause zsh to load
# a theme from this variable instead of looking in $ZSH/themes/
# If set to an empty array, this variable will have no effect.
# ZSH_THEME_RANDOM_CANDIDATES=( "robbyrussell" "agnoster" )

# Uncomment the following line to use case-sensitive completion.
# CASE_SENSITIVE="true"

# Uncomment the following line to use hyphen-insensitive completion.
# Case-sensitive completion must be off. _ and - will be interchangeable.
# HYPHEN_INSENSITIVE="true"

# Uncomment one of the following lines to change the auto-update behavior
# zstyle ':omz:update' mode disabled  # disable automatic updates
# zstyle ':omz:update' mode auto      # update automatically without asking
# zstyle ':omz:update' mode reminder  # just remind me to update when it's time

# Uncomment the following line to change how often to auto-update (in days).
# zstyle ':omz:update' frequency 13
zstyle ':completion:*' matcher-list 'm:{a-zA-Z}={A-Za-z}' 'r:|=*' 'l:|=* r:|=*'
# Uncomment the following line if pasting URLs and other text is messed up.
# DISABLE_MAGIC_FUNCTIONS="true"

# Uncomment the following line to disable colors in ls.
# DISABLE_LS_COLORS="true"

# Uncomment the following line to disable auto-setting terminal title.
DISABLE_AUTO_TITLE="true"

# Uncomment the following line to enable command auto-correction.
ENABLE_CORRECTION="true"

# Uncomment the following line to display red dots whilst waiting for completion.
# You can also set it to another string to have that shown instead of the default red dots.
# e.g. COMPLETION_WAITING_DOTS="%F{yellow}waiting...%f"
# Caution: this setting can cause issues with multiline prompts in zsh < 5.7.1 (see #5765)
# COMPLETION_WAITING_DOTS="true"

# Uncomment the following line if you want to disable marking untracked files
# under VCS as dirty. This makes repository status check for large repositories
# much, much faster.
# DISABLE_UNTRACKED_FILES_DIRTY="true"

# Uncomment the following line if you want to change the command execution time
# stamp shown in the history command output.
# You can set one of the optional three formats:
# "mm/dd/yyyy"|"dd.mm.yyyy"|"yyyy-mm-dd"
# or set a custom format using the strftime function format specifications,
# see 'man strftime' for details.
# HIST_STAMPS="mm/dd/yyyy"

# Would you like to use another custom folder than $ZSH/custom?
# ZSH_CUSTOM=/path/to/new-custom-folder

# Which plugins would you like to load?
# Standard plugins can be found in $ZSH/plugins/
# Custom plugins may be added to $ZSH_CUSTOM/plugins/
# Example format: plugins=(rails git textmate ruby lighthouse)
# Add wisely, as too many plugins slow down shell startup.
# plugins=(git zsh-autosuggestions zsh-syntax-highlighting history)
plugins=(
    git
    zsh-autosuggestions
    zsh-syntax-highlighting
    history
    macos
    brew
    python
    pip
)

# source $ZSH/oh-my-zsh.sh

# User configuration

alias nacos-start='sudo sh /Users/allen/ai-infra/nacos/standalone/nacos-3.2.1-2026.03.30/bin/startup.sh -m standalone'
alias nacos-stop='sudo sh /Users/allen/ai-infra/nacos/standalone/nacos-3.2.1-2026.03.30/bin/shutdown.sh'
# export MANPATH="/usr/local/man:$MANPATH"
export PATH="/usr/local/opt/python@3.14/libexec/bin:$PATH"
export PATH="/usr/local/mysql/bin:$PATH"
# export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
# export PATH=$JAVA_HOME/bin:$PATH

# ==================== Java 多版本管理 ====================
# 设置各版本 JAVA_HOME 路径（基于您的实际安装）
export JAVA_17_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export JAVA_21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/usr/local/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home")"

# 函数：快速切换 JDK 版本
setjdk() {
    if [ $# -ne 1 ]; then
        echo "Usage: setjdk <version>"
        echo "Available versions: 17, 21"
        return 1
    fi
    case "$1" in
        17)
            export JAVA_HOME="$JAVA_17_HOME"
            ;;
        21)
            export JAVA_HOME="$JAVA_21_HOME"
            ;;
        *)
            echo "Unsupported JDK version: $1"
            echo "Supported versions: 17, 21"
            return 1
            ;;
    esac
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Switched to JDK $1 at $JAVA_HOME"
    java -version
}

# 默认使用 JDK 21（如需默认 17，请改为 setjdk 17）
setjdk 21

# 可选：启用 setjdk 命令的自动补全（Zsh 插件）
compdef '_values "JDK versions" 17 21' setjdk
# ======================================================


# 3. 告诉 Homebrew 从 ghcr.io 拉取预编译包，并指定 Java 路径
export HOMEBREW_NO_INSTALL_FROM_API=1
export HOMEBREW_MAKE_JOBS=1
export HOMEBREW_BOTTLE_DOMAIN="https://ghcr.io/v2/homebrew/core"
export HOMEBREW_BUILD_FROM_SOURCE=0
export HOMEBREW_INSTALL_BOTTLE=1
export HOMEBREW_CORE_GIT_REMOTE="https://mirrors.ustc.edu.cn/homebrew-core.git"
# rocketmq
export ROCKETMQ_HOME=/usr/local/rocketmq
export PATH=$PATH:$ROCKETMQ_HOME/bin

# RocketMQ 管理命令
export NAMESRV_ADDR="127.0.0.1:9876"

rocketmq() {
    local cmd=$1
    case $cmd in
        start)
            echo "Starting RocketMQ..."
            nohup sh $ROCKETMQ_HOME/bin/mqnamesrv > $ROCKETMQ_HOME/logs/namesrv.log 2>&1 &
            sleep 2
            nohup sh $ROCKETMQ_HOME/bin/mqbroker -n localhost:9876 -c $ROCKETMQ_HOME/conf/broker.conf autoCreateTopicEnable=true > $ROCKETMQ_HOME/logs/broker.log 2>&1 &
            echo "RocketMQ started."
            ;;
        stop)
            echo "Stopping RocketMQ..."
            sh $ROCKETMQ_HOME/bin/mqshutdown broker
            sh $ROCKETMQ_HOME/bin/mqshutdown namesrv
            echo "RocketMQ stopped."
            ;;
        status)
            # 检查 NameServer 和 Broker 进程是否存在
            if pgrep -f "mqnamesrv" > /dev/null; then
                echo "NameServer is running."
            else
                echo "NameServer is NOT running."
            fi
            if pgrep -f "mqbroker" > /dev/null; then
                echo "Broker is running."
            else
                echo "Broker is NOT running."
            fi
            ;;
        restart)
            rocketmq stop
            sleep 2
            rocketmq start
            ;;
        logs)
            echo "Tailing NameServer log..."
            tail -f $ROCKETMQ_HOME/logs/namesrv.log
            ;;
        *)
            echo "Usage: rocketmq {start|stop|restart|status|logs}"
            ;;
    esac
}
# You may need to manually set your language environment
# export LANG=en_US.UTF-8

# Preferred editor for local and remote sessions
# if [[ -n $SSH_CONNECTION ]]; then
#   export EDITOR='vim'
# else
#   export EDITOR='nvim'
# fi

# Compilation flags
# export ARCHFLAGS="-arch $(uname -m)"

# Set personal aliases, overriding those provided by Oh My Zsh libs,
# plugins, and themes. Aliases can be placed here, though Oh My Zsh
# users are encouraged to define aliases within a top-level file in
# the $ZSH_CUSTOM folder, with .zsh extension. Examples:
# - $ZSH_CUSTOM/aliases.zsh
# - $ZSH_CUSTOM/macos.zsh
# For a full list of active aliases, run `alias`.
#
# Example aliases
# alias zshconfig="mate ~/.zshrc"
# alias ohmyzsh="mate ~/.oh-my-zsh"

# 设置历史命令文件路径
HISTFILE=~/.zsh_history

# 设置历史命令最大保存数量
HISTSIZE=10000
SAVEHIST=10000

# 将每条命令添加到历史文件中
setopt INC_APPEND_HISTORY

# 忽略重复命令
setopt HIST_IGNORE_DUPS

# 忽略以空格开头的命令
setopt HIST_IGNORE_SPACE

# 共享历史命令（多个终端会话共享历史）
setopt SHARE_HISTORY

source $ZSH/oh-my-zsh.sh

# === 优化显示 ===
# 修复 agnoster 主题路径显示问题
prompt_context() {
  if [[ "$USER" != "$DEFAULT_USER" || -n "$SSH_CLIENT" ]]; then
    prompt_segment black default "%(!.%{%F{yellow}%}.)%n@%m"
  fi
}

# 设置更好的颜色（适合深色背景）
export LSCOLORS="ExGxBxDxCxEgEdxbxgxcxd"
export CLICOLOR=1

# 优化提示符颜色
PROMPT='%{%F{green}%}➜ %{%F{cyan}%}%c%{%F{red}%}$(git_prompt_info)%{%F{white}%} '

# Added by nacos-setup installer
export PATH="$HOME/.nacos/bin:$PATH"

. "$HOME/.local/bin/env"
export HOMEBREW_NO_AUTO_UPDATE=1
export HOMEBREW_BOTTLE_DOMAIN=https://mirrors.ustc.edu.cn/homebrew-bottles
