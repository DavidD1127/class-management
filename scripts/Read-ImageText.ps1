param([Parameter(Mandatory=$true)][string]$ImagePath)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null=[Windows.Storage.StorageFile,Windows.Storage,ContentType=WindowsRuntime]
$null=[Windows.Graphics.Imaging.BitmapDecoder,Windows.Foundation,ContentType=WindowsRuntime]
$null=[Windows.Media.Ocr.OcrEngine,Windows.Foundation,ContentType=WindowsRuntime]
$null=[Windows.Globalization.Language,Windows.Globalization,ContentType=WindowsRuntime]
$asTask=[System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {$_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'} | Select-Object -First 1
function Await($operation,[Type]$resultType){$task=$asTask.MakeGenericMethod($resultType).Invoke($null,@($operation));$task.GetAwaiter().GetResult()}
$file=Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync([IO.Path]::GetFullPath($ImagePath))) ([Windows.Storage.StorageFile])
$stream=Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
try{
 $decoder=Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
 if([long]$decoder.PixelWidth*$decoder.PixelHeight -gt 24000000){throw 'Image exceeds 24 megapixels'}
 $bitmap=Await ($decoder.GetSoftwareBitmapAsync([Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8,[Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied)) ([Windows.Graphics.Imaging.SoftwareBitmap])
 try{
  $language=New-Object Windows.Globalization.Language('zh-Hans-CN')
  $engine=[Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($language)
  if($null -eq $engine){$engine=[Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()}
  if($null -eq $engine){throw 'No OCR recognition language is installed'}
  $result=Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
  # Rebuild visual rows; OCR can otherwise return all numbers before all names.
  $words=@($result.Lines | ForEach-Object {$_.Words} | Sort-Object @{Expression={$_.BoundingRect.Y+$_.BoundingRect.Height/2}},@{Expression={$_.BoundingRect.X}})
  $rows=New-Object Collections.Generic.List[object]
  foreach($word in $words){
   $center=$word.BoundingRect.Y+$word.BoundingRect.Height/2
   $last=if($rows.Count){$rows[$rows.Count-1]}else{$null}
   if($null -eq $last -or [Math]::Abs($center-$last.center) -gt [Math]::Max(8,$word.BoundingRect.Height*0.6)){
    $last=@{center=$center;words=(New-Object Collections.Generic.List[object])};$rows.Add($last)
   }
   $last.words.Add($word)
  }
  $lines=foreach($row in $rows){
   $line='';$previous=$null
   foreach($word in ($row.words | Sort-Object @{Expression={$_.BoundingRect.X}})){
    if($null -ne $previous){$gap=$word.BoundingRect.X-($previous.BoundingRect.X+$previous.BoundingRect.Width);if($gap -gt [Math]::Max(14,$word.BoundingRect.Height*1.2)){$line+="`t"}}
    $line+=$word.Text;$previous=$word
   }
   $line
  }
  @{text=($lines -join "`n")}|ConvertTo-Json -Compress
 }finally{$bitmap.Dispose()}
}finally{$stream.Dispose()}
