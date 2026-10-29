<script setup lang="ts">
import type { Season } from '../experience/seasons'
withDefaults(defineProps<{ season?: Season; label?: string }>(), { season: 'spring' })
</script>

<template>
  <!-- Original vector artwork, drawn for this project. No external image assets. -->
  <svg viewBox="0 0 720 440" class="season-artwork" :class="`art-${season}`" :role="label ? 'img' : undefined" :aria-label="label" :aria-hidden="label ? undefined : true" preserveAspectRatio="xMidYMid slice">
    <circle class="art-sun" cx="523" cy="108" r="53"/>
    <path class="art-mountain far" d="M-40 319 104 163Q121 145 148 167L242 247 334 139Q357 113 388 146L558 296 664 196Q688 180 723 204L770 268V440H-40Z"/>
    <path class="art-mountain near" d="M-40 366 131 262Q159 247 188 262L322 331 465 236Q492 216 520 238L770 367V440H-40Z"/>
    <path class="art-river" d="M-30 375Q151 324 309 380T760 363M-30 398Q150 347 309 403T760 386M-30 422Q151 371 309 427T760 410"/>
    <path class="art-branch" d="M68 440Q84 300 155 215M93 338Q44 321 35 281M114 287Q172 283 199 244M145 228Q124 185 137 147"/>
    <g v-if="season === 'spring'" class="art-leaf"><path d="M94 338Q28 337 28 282Q75 277 94 338M115 288Q145 226 201 242Q193 289 115 288M143 223Q108 173 139 145Q173 178 143 223"/><path d="M563 441Q551 369 589 330Q621 371 563 441M561 405Q516 397 514 368Q552 363 561 405"/></g>
    <g v-else-if="season === 'summer'" class="art-leaf"><path d="M106 325Q42 311 36 275Q91 270 106 325M126 274Q163 221 201 240Q180 281 126 274"/><path d="M476 407Q504 337 543 331Q578 355 595 407Q523 427 476 407M531 413V440"/><path class="art-flower" d="M524 370Q493 327 515 308Q535 304 539 338Q545 296 567 308Q586 329 552 367Z"/></g>
    <g v-else-if="season === 'autumn'" class="art-leaf"><path d="M94 338 40 317 34 278 73 293ZM119 284 156 244 200 245 180 280ZM142 223 126 181 139 147 161 181Z"/><path d="m510 244 18-21 19 26-20 14ZM589 299l11-20 22 18-16 16ZM451 309l17-16 16 21-20 9Z"/></g>
    <g v-else class="art-snow"><path d="m105 164 20 24 23-21 39 36-32-12-28 15ZM335 139q22-26 53 7l42 38-41-12-20 14-18-22-23 17-17-16Z"/><circle cx="205" cy="117" r="3"/><circle cx="410" cy="75" r="3"/><circle cx="586" cy="230" r="3"/><circle cx="257" cy="211" r="2"/><circle cx="632" cy="126" r="2"/></g>
    <g class="art-house"><path d="M331 342v-47h71v47M319 295l47-29 48 29ZM355 342v-28h20v28"/><path d="M430 342h62m-40-12h21"/></g>
    <g class="art-birds"><path d="M432 161q9-11 18 0 8-11 17 0M472 193q7-8 14 0 7-8 14 0"/></g>
  </svg>
</template>
