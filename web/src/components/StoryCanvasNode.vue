<script setup lang="ts">
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { useI18n } from 'vue-i18n'
import { nodeKinds } from '../experience/editor-model'
import type { NodeKind } from '../api/editor'
export interface CanvasData { kind: NodeKind; zh: string; en: string; start: boolean; actions: string[] }
defineProps<NodeProps<CanvasData>>()
const { locale } = useI18n()
</script>
<template>
  <div class="story-canvas-node" :class="[`kind-${data.kind}`, { 'is-selected': selected }]" :data-node-key="id">
    <Handle type="target" :position="Position.Left" id="in"/>
    <div class="canvas-node-top"><span>{{ locale === 'en' ? nodeKinds.find(k => k.type === data.kind)?.en : nodeKinds.find(k => k.type === data.kind)?.zh }}</span><small v-if="data.start">{{ locale === 'en' ? 'START' : '起点' }}</small></div>
    <h3>{{ (locale === 'en' ? data.en : data.zh) || (locale === 'en' ? 'Untitled' : '未命名节点') }}</h3><code>{{ id }}</code>
    <div v-for="action in data.actions" :key="action" class="canvas-node-action"><span>{{ action }}</span><Handle type="source" :position="Position.Right" :id="action"/></div>
    <p v-if="data.kind === 'ending'" class="canvas-node-terminal">{{ locale === 'en' ? 'Server settlement' : '服务端结局与奖励' }}</p>
  </div>
</template>
