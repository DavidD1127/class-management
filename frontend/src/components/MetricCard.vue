<template><div class="metric-card"><p>{{label}}</p><strong>{{value}}</strong><small>{{detail}}</small></div></template>
<script setup>defineProps({label:String,value:[String,Number],detail:String});</script>
