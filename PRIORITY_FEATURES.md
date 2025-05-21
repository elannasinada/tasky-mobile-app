# Task Priority Management in Tasky

This document describes the task priority management features in Tasky, including AI-suggested
priorities and manual priority selection.

## Overview

Tasky implements an intelligent task priority system that combines AI suggestions with user control.
This system helps users effectively prioritize their tasks while maintaining full control over their
task management.

## Key Features

### AI-Suggested Priorities

When creating a new task, the system can automatically suggest an appropriate priority level based
on:

- Task title and description content
- Deadline proximity
- Task type
- Other contextual factors

The AI analyzes these factors and assigns one of three priority levels:

- **Low**: Default priority for routine tasks
- **Medium**: Tasks that deserve special attention
- **High**: Critical tasks that require immediate attention

### Manual Priority Selection

Users can manually select a priority for any task:

1. When creating a new task
2. When editing an existing task
3. To override an AI suggestion

Once a priority is manually selected, it is marked as "manually set" and will be preserved even when
the task is edited.

### Priority Persistence

The system implements a clear priority hierarchy:

1. **User-selected priorities always take precedence**. When a user manually sets a priority, it is
   permanently saved with the task and will never be overridden by AI.
2. **AI suggestions are applied only when no manual priority exists**. If a user hasn't explicitly
   set a priority, the AI will suggest one.

## User Interface

The priority selection interface in Tasky clearly indicates:

- When a priority is AI-suggested vs. manually set
- Which priority is currently selected
- That manual selections will be permanently saved

### UI Components

- **Priority Selection Buttons**: Three buttons for Low, Medium, and High priority
- **AI Suggestion Indicator**: Shows when an AI has suggested the current priority
- **Manual Selection Indicator**: Shows when the priority was manually set by the user
- **Explanatory Text**: Explains that manual selections will be saved permanently

## Technical Implementation

The task priority system is implemented with these key components:

1. **Task Model**: Includes `priority` (0=Low, 1=Medium, 2=High) and `isPriorityManuallySet` (
   boolean) fields
2. **TaskDetailsViewModel**: Handles logic for determining when to use AI vs. manual priorities
3. **GeminiService**: Uses Google's Gemini API to analyze task data and suggest appropriate
   priorities
4. **UI Components**: Provide visual feedback about priority status

## Configuration

To use the AI priority suggestions, you need a Google Gemini API key:

1. Get a key from [Google AI Studio](https://aistudio.google.com/)
2. Add it to `local.properties` in your project root:
   ```
   gemini.api.key=YOUR_API_KEY
   ```

If no API key is provided, the app will still function but will not offer AI priority suggestions.

## Best Practices

- **Let AI handle routine prioritization** to save time
- **Manually set priorities for tasks** where you have specific knowledge about their importance
- **Start with AI suggestions** and override them only when necessary