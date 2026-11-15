const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const money = n => new Intl.NumberFormat('en-NG',{style:'currency',currency:'NGN',maximumFractionDigits:2}).format(Number(n||0));
const dateText = v => v ? new Date(v).toLocaleString('en-NG',{dateStyle:'medium',timeStyle:'short'}) : '—';
function esc(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
const csrf=()=>{const m=document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);return m?decodeURIComponent(m[1]):''};
let toastTimer;
function toast(message){const el=$('#toast');el.textContent=message;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),3500)}
const fail=e=>toast(e.message);

let netUp=true;                      // false after a request fails to reach the server
function setNet(up){netUp=up;renderNet()}
function renderNet(){
 const b=$('#netBadge');if(!b)return;
 const n=Offline.listPending().length,online=netUp&&navigator.onLine!==false;
 b.className='net-badge '+(online?'net-ok':'net-off');
 b.textContent=(online?'● Online':'○ Offline: sales are saved on this device')+(n?` · ${n} waiting to upload`:'');
}
async function api(url, options={}) {
 const {noAuthRedirect,...opts}=options;
 let response;
 try{response=await fetch(url,{credentials:'same-origin',...opts,headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf(),...(opts.headers||{})}})}
 catch{setNet(false);throw Object.assign(new Error('You are offline. Check the internet connection.'),{offline:true})}
 setNet(true);
 if(response.status===401){
  if(noAuthRedirect)throw Object.assign(new Error('Please sign in again to upload your offline sales.'),{auth:true});
  location.href='/login.html';throw new Error('Please sign in again.')
 }
 if(!response.ok){let message='Request failed';try{const body=await response.json();message=body.error||body.message||message}catch{}throw Object.assign(new Error(message),{status:response.status})}
 if(response.status===204)return null;
 return response.json();
}
const debounce=(fn,ms)=>{let t;return(...a)=>{clearTimeout(t);t=setTimeout(()=>fn(...a),ms)}};

// ---------- state
const PAGE=25;                       // rows per page everywhere
const pg={products:0,purchases:0,cash:0,sales:0,deletable:0,report:0};
let me={username:'',role:'ADMIN',roleLabel:'Inventory Admin'};
const isAdmin=()=>me.role==='ADMIN';
let products=[],cashRows=[],salesRows=[],cart=[],picked=null,searchResults=[],reportData=null,usersList=[];
let settings={businessName:'My Shop',address:'',phone:'',logoData:null};

// ---------- pager: "Showing 26–50 of 1,000" with Prev / 1 2 3 … / Next
function renderPager(sel,info,goTo){
 const el=$(sel);
 if(!info||!info.totalElements){el.innerHTML='';return}
 const cur=info.page,last=info.totalPages-1;
 const from=cur*info.size+1,to=Math.min(info.totalElements,(cur+1)*info.size);
 let html=`<span class="muted small">Showing ${from.toLocaleString()}–${to.toLocaleString()} of ${info.totalElements.toLocaleString()}</span>`;
 if(last>0){
  const pages=[];for(let i=0;i<=last;i++)if(i===0||i===last||Math.abs(i-cur)<=2)pages.push(i);
  html+='<div class="pager-btns">'+`<button type="button" data-p="${cur-1}" ${cur===0?'disabled':''}>‹ Prev</button>`;
  let prev=-1;
  pages.forEach(i=>{if(prev!==-1&&i-prev>1)html+='<span>…</span>';html+=`<button type="button" data-p="${i}" class="${i===cur?'active':''}">${i+1}</button>`;prev=i});
  html+=`<button type="button" data-p="${cur+1}" ${cur===last?'disabled':''}>Next ›</button></div>`;
 }
 el.innerHTML=html;
 el.querySelectorAll('button[data-p]').forEach(b=>b.onclick=()=>goTo(Number(b.dataset.p)));
}

// ---------- navigation and roles
function go(page){
 if(!isAdmin()&&page!=='sales')page='sales';
 $$('.page').forEach(p=>p.classList.add('hidden'));$('#page-'+page).classList.remove('hidden');
 $$('.nav-item[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
 loadPage(page);
}
$$('.nav-item[data-page]').forEach(b=>b.addEventListener('click',()=>go(b.dataset.page)));
$$('[data-go]').forEach(b=>b.addEventListener('click',()=>go(b.dataset.go)));
$('#today').textContent=new Date().toLocaleDateString('en-NG',{dateStyle:'medium'});
function applyRole(){
 $('#userName').textContent=me.username;$('#userRole').textContent='· '+me.roleLabel;$('#userAvatar').textContent=(me.username[0]||'?').toUpperCase();
 if(!isAdmin())$$('.nav-item[data-page]').forEach(b=>{if(b.dataset.page!=='sales')b.classList.add('hidden')});
}

// ---------- date range chips (today / week / month / year / custom)
function makeRange(sel, options, initial, onChange){
 const el=$(sel);
 const labels={today:'Today',week:'This week',month:'This month',year:'This year',all:'All time',custom:'Custom dates'};
 const state={range:initial,from:'',to:''};
 el.innerHTML=options.map(o=>`<button type="button" class="chip ${o===initial?'active':''}" data-range="${o}">${labels[o]}</button>`).join('')
  +'<span class="custom-dates hidden"><input type="date" class="d-from"><span>to</span><input type="date" class="d-to"><button type="button" class="btn secondary d-apply">Apply</button></span>';
 const custom=el.querySelector('.custom-dates');
 el.querySelectorAll('.chip').forEach(b=>b.onclick=()=>{
  state.range=b.dataset.range;
  el.querySelectorAll('.chip').forEach(x=>x.classList.toggle('active',x===b));
  custom.classList.toggle('hidden',state.range!=='custom');
  if(state.range!=='custom')onChange();
 });
 el.querySelector('.d-apply').onclick=()=>{
  state.from=el.querySelector('.d-from').value;state.to=el.querySelector('.d-to').value;
  if(!state.from||!state.to){toast('Choose both a start date and an end date.');return}
  onChange();
 };
 state.query=()=>'range='+state.range+(state.range==='custom'?'&from='+state.from+'&to='+state.to:'');
 state.ready=()=>state.range!=='custom'||(state.from&&state.to);
 return state;
}

// ---------- business branding
function applyBranding(){
 $('#brandName').textContent=settings.businessName||'InventoryPro';
 $('#shopName').textContent=settings.businessName||'';
 $('#shopAddress').textContent=settings.address||'';
 $('#brandIcon').innerHTML=settings.logoData?`<img src="${esc(settings.logoData)}" alt="">`:'🛒';
 document.title=settings.businessName||'InventoryPro';
}
async function loadBranding(){
 try{settings=await api('/api/settings');applyBranding();Offline.setMeta('settings',settings).catch(()=>{})}
 catch(e){
  if(!e.offline){toast(e.message);return}
  try{const saved=await Offline.getMeta('settings');if(saved){settings=saved;applyBranding()}}catch{}
 }
}

// ---------- dashboard
async function loadDashboard(){
 const d=await api('/api/dashboard');
 $('#dashCash').textContent=money(d.cashBalance);$('#cashBalance').textContent=money(d.cashBalance);
 $('#productCount').textContent=d.productCount;$('#totalStock').textContent=d.totalStock;
 $('#lowCount').textContent=d.lowStockCount;$('#salesTotal').textContent=money(d.salesTotal);
 $('#purchaseTotal').textContent=money(d.purchaseTotal);
 $('#stockBar').style.width=(d.productCount?Math.min(100,((d.productCount-d.lowStockCount)/d.productCount)*100):0)+'%';
 $('#lowStockList').innerHTML=d.lowStockProducts.length?d.lowStockProducts.slice(0,6).map(p=>`<div class="alert-row"><span>${esc(p.name)} <small class="muted">(${p.quantity} left)</small></span><span class="pill ${p.quantity===0?'critical':''}">${p.quantity===0?'Out of stock':'Low stock'}</span></div>`).join(''):'<div class="empty">No low-stock products. Great work!</div>';
}
const cashRowHtml=t=>`<tr><td>${dateText(t.createdAt)}</td><td>${esc(t.type.replaceAll('_',' '))}</td><td style="color:${Number(t.amount)>=0?'#078b55':'#c52d3c'}">${money(t.amount)}</td><td>${money(t.balanceAfter)}</td><td>${esc(t.description)}</td></tr>`;
async function loadRecentCash(){
 const r=await api('/api/cash/transactions?page=0&size=10');
 $('#recentCash').innerHTML=r.items.map(cashRowHtml).join('')||'<tr><td colspan="5" class="empty">No cash transactions yet.</td></tr>';
}

// ---------- products (paged)
async function loadProducts(){
 const q=$('#productSearch').value.trim();
 const r=await api(`/api/products?page=${pg.products}&size=${PAGE}`+(q?'&q='+encodeURIComponent(q):''));
 if(!r.items.length&&pg.products>0){pg.products=Math.max(0,r.totalPages-1);return loadProducts()}
 products=r.items;
 const offset=pg.products*PAGE;
 $('#productsTable').innerHTML=products.length?products.map((p,i)=>`<tr><td>${offset+i+1}</td><td><b>${esc(p.name)}</b></td><td>${esc(p.sku)}</td><td>${esc(p.category)}</td><td><span class="pill ${p.quantity<=p.reorderLevel?'critical':''}">${p.quantity}</span></td><td>${money(p.costPrice)}</td><td>${money(p.sellingPrice)}</td><td><button class="btn secondary edit-product" data-id="${p.id}">Edit</button> <button class="btn danger delete-product" data-id="${p.id}">Delete</button></td></tr>`).join(''):'<tr><td colspan="8" class="empty">No products found.</td></tr>';
 $('#productTotal').textContent=`${r.totalElements.toLocaleString()} product(s)`;
 $$('.edit-product').forEach(b=>b.onclick=()=>editProduct(Number(b.dataset.id)));
 $$('.delete-product').forEach(b=>b.onclick=()=>deleteProduct(Number(b.dataset.id)));
 renderPager('#productsPager',r,p=>{pg.products=p;loadProducts().catch(fail)});
}
async function loadProductOptions(){
 const all=await api('/api/products/all');
 $$('.product-select').forEach(sel=>{const old=sel.value;sel.innerHTML=all.map(p=>`<option value="${p.id}">${esc(p.name)} — ${esc(p.sku)} (stock: ${p.quantity})</option>`).join('');if(old)sel.value=old});
}
function openModal(p=null){
 const form=$('#productForm');form.reset();form.elements.id.value=p?.id||'';$('#debitRow').classList.toggle('hidden',!!p);
 $('#modalTitle').textContent=p?'Edit Product':'Add Product';
 if(p)Object.entries(p).forEach(([k,v])=>{if(form.elements[k])form.elements[k].value=v});
 $('#productModal').classList.remove('hidden');
}
function closeModal(){$('#productModal').classList.add('hidden')}
$('#newProductBtn').onclick=()=>openModal();
$('#closeModal').onclick=closeModal;$('#cancelModal').onclick=closeModal;
async function editProduct(id){const p=products.find(x=>x.id===id);if(p)openModal(p)}
async function deleteProduct(id){if(!confirm('Delete this product?'))return;try{await api('/api/products/'+id,{method:'DELETE'});toast('Product deleted');await loadProducts()}catch(e){toast(e.message)}}
$('#productForm').onsubmit=async e=>{
 e.preventDefault();const f=e.target;const d=Object.fromEntries(new FormData(f));const id=d.id;delete d.id;const debit=d.debitCash==='on';delete d.debitCash;
 ['quantity','reorderLevel'].forEach(k=>d[k]=Number(d[k]));['costPrice','sellingPrice'].forEach(k=>d[k]=Number(d[k]));
 try{await api(id?'/api/products/'+id:'/api/products?debitCash='+debit,{method:id?'PUT':'POST',body:JSON.stringify(d)});closeModal();toast(id?'Product updated':(debit?'Product added. Stock cost debited from cash balance.':'Product added.'));await loadProducts()}catch(err){toast(err.message)}
};
$('#productSearch').oninput=debounce(()=>{pg.products=0;loadProducts().catch(fail)},250);

$('#downloadProductTemplate').onclick=()=>{location.href='/api/products/template'};
$('#importProductsBtn').onclick=()=>$('#productExcelFile').click();
$('#productExcelFile').onchange=async e=>{
 const file=e.target.files[0]; if(!file)return;
 const result=$('#excelImportResult'); result.classList.remove('hidden'); result.textContent='Importing products...';
 const form=new FormData(); form.append('file',file);
 try{
  const response=await fetch('/api/products/import?debitCash='+($('#importDebit').checked),{method:'POST',body:form,credentials:'same-origin',headers:{'X-XSRF-TOKEN':csrf()}});
  if(response.status===401){location.href='/login.html';return}
  const data=await response.json(); if(!response.ok)throw new Error(data.error||'Excel import failed.');
  result.innerHTML=`<b>Import complete:</b> ${data.imported} imported, ${data.skipped} skipped.`+(data.errors.length?'<ul>'+data.errors.map(x=>`<li>${esc(x)}</li>`).join('')+'</ul>':'');
  toast(`${data.imported} product(s) imported`); pg.products=0; await loadProducts();
 }catch(err){result.textContent=err.message;toast(err.message)}finally{e.target.value=''}
};

// ---------- cash (paged)
async function loadCash(){
 const [bal,r]=await Promise.all([api('/api/cash'),api(`/api/cash/transactions?page=${pg.cash}&size=${PAGE}`)]);
 if(!r.items.length&&pg.cash>0){pg.cash=Math.max(0,r.totalPages-1);return loadCash()}
 $('#cashBalance').textContent=money(bal.balance);$('#dashCash').textContent=money(bal.balance);
 $('#cashTable').innerHTML=r.items.map(cashRowHtml).join('')||'<tr><td colspan="5" class="empty">No cash transactions yet.</td></tr>';
 renderPager('#cashPager',r,p=>{pg.cash=p;loadCash().catch(fail)});
}
async function submitCash(e,action){
 e.preventDefault();const d=Object.fromEntries(new FormData(e.target));d.amount=Number(d.amount);
 try{await api('/api/cash/'+action,{method:'POST',body:JSON.stringify(d)});e.target.reset();toast(action==='add'?'Cash added successfully':'Cash withdrawn successfully');pg.cash=0;await loadCash()}catch(err){toast(err.message)}
}
$('#cashInForm').onsubmit=e=>submitCash(e,'add');
$('#cashOutForm').onsubmit=e=>submitCash(e,'withdraw');
function csvDownload(name,rows){
 if(!rows.length){toast('There is no data to export');return}
 const csv=rows.map(row=>row.map(v=>'"'+String(v??'').replace(/"/g,'""')+'"').join(',')).join('\r\n');
 const blob=new Blob(['﻿'+csv],{type:'text/csv;charset=utf-8;'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;a.click();URL.revokeObjectURL(url);
}
$('#exportCash').onclick=async()=>{
 try{
  const rows=[];let page=0,pages=1;
  while(page<pages){const r=await api(`/api/cash/transactions?page=${page}&size=100`);rows.push(...r.items);pages=r.totalPages;page++}
  csvDownload('cash-transactions.csv',[['Date','Type','Amount','Balance after','Description'],...rows.map(t=>[t.createdAt,t.type,t.amount,t.balanceAfter,t.description])]);
 }catch(e){toast(e.message)}
};

// ---------- purchases (paged)
async function loadPurchases(){
 const r=await api(`/api/purchases?page=${pg.purchases}&size=${PAGE}`);
 if(!r.items.length&&pg.purchases>0){pg.purchases=Math.max(0,r.totalPages-1);return loadPurchases()}
 $('#purchasesTable').innerHTML=r.items.map(t=>`<tr><td>${dateText(t.createdAt)}</td><td>${esc(t.product.name)}</td><td>${t.quantity}</td><td>${money(t.unitPrice)}</td><td>${money(t.total)}</td><td>${esc(t.partyName||'—')}</td></tr>`).join('')||'<tr><td colspan="6" class="empty">No records yet.</td></tr>';
 renderPager('#purchasesPager',r,p=>{pg.purchases=p;loadPurchases().catch(fail)});
}
$('#purchaseForm').onsubmit=async e=>{
 e.preventDefault();const d=Object.fromEntries(new FormData(e.target));d.productId=Number(d.productId);d.quantity=Number(d.quantity);d.unitPrice=Number(d.unitPrice);
 try{await api('/api/purchases',{method:'POST',body:JSON.stringify(d)});e.target.reset();toast('Purchase recorded. Cash reduced and stock increased.');pg.purchases=0;await loadProductOptions();await loadPurchases()}catch(err){toast(err.message)}
};

// ---------- sales: server-side product search + cart
const inCart=id=>cart.filter(l=>l.productId===id).reduce((n,l)=>n+l.qty,0);
const cartTotal=()=>cart.reduce((n,l)=>n+l.qty*l.price,0);
try{$('#printReceipt').checked=localStorage.getItem('printReceipt')==='1'}catch{}
$('#printReceipt').onchange=e=>{try{localStorage.setItem('printReceipt',e.target.checked?'1':'0')}catch{}};

let searchSeq=0;
async function runSearch(){
 const seq=++searchSeq,q=$('#saleSearch').value.trim(),box=$('#saleResults');
 let list;
 try{list=await api('/api/products/search?limit=8'+(q?'&q='+encodeURIComponent(q):''))}
 catch(e){
  if(!e.offline){toast(e.message);return}
  try{
   if(!(await Offline.catalogCount())){toast('You are offline and the product list is not saved on this device yet. Connect to the internet once.');return}
   list=await Offline.searchCatalog(q,8);
  }catch(err){toast('Offline product list is not available: '+err.message);return}
 }
 if(seq!==searchSeq)return;          // a newer search already replaced this one
 // stock already sold on this device but not uploaded yet is not available any more
 searchResults=list.map(p=>({...p,quantity:Math.max(0,p.quantity-Offline.pendingQty(p.id))}));
 const note=!netUp?'<div class="result out" style="cursor:default"><small>Offline: showing the product list saved on this device</small></div>':'';
 if(!searchResults.length){box.innerHTML=note+'<div class="result out">No matching product</div>';box.classList.remove('hidden');return}
 box.innerHTML=note+searchResults.map((p,i)=>`<div class="result ${p.quantity<=0?'out':''} ${i===0?'active':''}" data-id="${p.id}"><span><b>${esc(p.name)}</b><small>${esc(p.sku)} · ${esc(p.category)}</small></span><span style="text-align:right"><b>${money(p.sellingPrice)}</b><small>${p.quantity<=0?'Out of stock':p.quantity+' in stock'}</small></span></div>`).join('');
 box.classList.remove('hidden');
 box.querySelectorAll('.result[data-id]').forEach(r=>r.onclick=()=>pick(Number(r.dataset.id)));
}
function pick(id){
 const p=searchResults.find(x=>x.id===id);if(!p)return;
 if(p.quantity<=0){toast(p.name+' is out of stock.');return}
 picked=p;
 $('#saleResults').classList.add('hidden');$('#saleSearch').value='';
 $('#pickName').textContent=p.name;
 $('#pickInfo').textContent=`${p.sku} · ${p.quantity} in stock`+(inCart(p.id)?` · ${inCart(p.id)} already in cart`:'');
 $('#pickPrice').value=p.sellingPrice;$('#pickQty').value=1;
 $('#pickPanel').classList.remove('hidden');$('#pickQty').focus();$('#pickQty').select();
}
$('#saleSearch').oninput=debounce(runSearch,200);
$('#saleSearch').onfocus=runSearch;
$('#saleSearch').onkeydown=e=>{
 if(e.key==='Enter'){e.preventDefault();const first=$('#saleResults .result[data-id]');if(first)pick(Number(first.dataset.id))}
 if(e.key==='Escape')$('#saleResults').classList.add('hidden');
};
document.addEventListener('click',e=>{if(!e.target.closest('.search-box'))$('#saleResults').classList.add('hidden')});

function addPickedToCart(){
 if(!picked)return;
 const qty=Number($('#pickQty').value),price=Number($('#pickPrice').value);
 if(!Number.isInteger(qty)||qty<1){toast('Enter a quantity of 1 or more.');return}
 if(!(price>0)){toast('Enter a selling price greater than zero.');return}
 if(inCart(picked.id)+qty>picked.quantity){toast(`Only ${picked.quantity} of ${picked.name} in stock (${inCart(picked.id)} already in cart).`);return}
 const same=cart.find(l=>l.productId===picked.id&&l.price===price);
 if(same)same.qty+=qty;else cart.push({productId:picked.id,name:picked.name,sku:picked.sku,qty,price,stock:picked.quantity});
 picked=null;$('#pickPanel').classList.add('hidden');renderCart();$('#saleSearch').focus();
}
$('#pickAdd').onclick=addPickedToCart;
$('#pickQty').onkeydown=$('#pickPrice').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();addPickedToCart()}};

function renderCart(){
 $('#cartTable').innerHTML=cart.length?cart.map((l,i)=>`<tr><td><b>${esc(l.name)}</b><br><small class="muted">${esc(l.sku)}</small></td><td><input class="qty" type="number" min="1" max="${l.stock}" value="${l.qty}" data-i="${i}" data-f="qty"></td><td><input type="number" min="0.01" step="0.01" value="${l.price}" data-i="${i}" data-f="price"></td><td>${money(l.qty*l.price)}</td><td><button class="btn danger small-btn" data-remove="${i}" type="button">✕</button></td></tr>`).join(''):'<tr><td colspan="5" class="empty">The cart is empty.</td></tr>';
 $('#cartTotal').textContent=money(cartTotal());
 $('#cartCount').textContent=cart.reduce((n,l)=>n+l.qty,0);
 $$('#cartTable input').forEach(inp=>inp.onchange=()=>{
  const l=cart[Number(inp.dataset.i)];const v=Number(inp.value);
  if(inp.dataset.f==='qty'){
   const others=inCart(l.productId)-l.qty;
   if(!Number.isInteger(v)||v<1||others+v>l.stock){toast(`Quantity must be between 1 and ${l.stock-others}.`)}else l.qty=v;
  }else{
   if(!(v>0))toast('Price must be greater than zero.');else l.price=v;
  }
  renderCart();
 });
 $$('[data-remove]').forEach(b=>b.onclick=()=>{cart.splice(Number(b.dataset.remove),1);renderCart()});
}
$('#clearCart').onclick=()=>{cart=[];renderCart()};

$('#completeSale').onclick=async()=>{
 if(!cart.length){toast('Add at least one product to the cart.');return}
 const body={customerName:$('#saleCustomer').value,note:$('#saleNote').value,clientId:Offline.uuid(),items:cart.map(l=>({productId:l.productId,quantity:l.qty,unitPrice:l.price}))};
 const lines=cart.map(l=>({...l}));
 const btn=$('#completeSale');btn.disabled=true;
 try{
  const sale=await api('/api/sales',{method:'POST',body:JSON.stringify(body)});
  cart=[];renderCart();$('#saleCustomer').value='';$('#saleNote').value='';
  Offline.decrementCatalog(body.items).catch(()=>{});
  toast(`Sale ${sale.receiptNo} recorded: ${money(sale.total)}`);
  pg.sales=0;await loadSalesHistory();
  if($('#printReceipt').checked)printReceipt(sale);
 }catch(err){
  if(err.offline)await saveOffline(body,lines);else toast(err.message);
 }finally{btn.disabled=false}
};

// ---------- offline sales: save on this device, upload when the internet is back
const pendingToSale=p=>({receiptNo:p.localNo,createdAt:p.soldAt,customerName:p.customerName,items:p.display.items,total:p.display.total,provisional:true});
async function saveOffline(body,lines){
 try{
  const rec={clientId:body.clientId,soldAt:new Date().toISOString(),customerName:body.customerName,note:body.note,items:body.items,
   localNo:await Offline.nextLocalNo(),status:'waiting',error:null,
   display:{total:lines.reduce((n,l)=>n+l.qty*l.price,0),items:lines.map(l=>({name:l.name,sku:l.sku,quantity:l.qty,unitPrice:l.price,lineTotal:l.qty*l.price}))}};
  await Offline.putPending(rec);
  cart=[];renderCart();$('#saleCustomer').value='';$('#saleNote').value='';
  renderPending();renderNet();
  toast(`No internet. Sale ${rec.localNo} was saved on this device and will upload automatically.`);
  if($('#printReceipt').checked)printReceipt(pendingToSale(rec));
 }catch(e){toast('The sale could not be saved offline, so it was NOT recorded: '+e.message)}   // the cart is kept
}
function renderPending(){
 const list=Offline.listPending();
 $('#pendingPanel').classList.toggle('hidden',!list.length);
 $('#pendingCount').textContent=list.length;
 $('#pendingTable').innerHTML=list.map(p=>`<tr><td>${esc(p.localNo)}</td><td>${dateText(p.soldAt)}</td><td class="items-cell">${p.display.items.map(i=>`${esc(i.name)} ×${i.quantity}`).join(', ')}</td><td>${money(p.display.total)}</td><td>${p.status==='failed'?`<span class="status-fail">Not accepted: ${esc(p.error)}</span>`:'<span class="status-wait">Waiting to upload</span>'}</td><td><button class="btn secondary small-btn" data-preceipt="${esc(p.clientId)}" type="button">Receipt</button> ${p.status==='failed'?`<button class="btn secondary small-btn" data-pretry="${esc(p.clientId)}" type="button">Retry</button> `:''}<button class="btn danger small-btn" data-pdiscard="${esc(p.clientId)}" type="button">Discard</button></td></tr>`).join('');
 const find=id=>Offline.listPending().find(x=>x.clientId===id);
 $$('[data-preceipt]').forEach(b=>b.onclick=()=>{const p=find(b.dataset.preceipt);if(p)printReceipt(pendingToSale(p))});
 $$('[data-pretry]').forEach(b=>b.onclick=async()=>{const p=find(b.dataset.pretry);if(!p)return;p.status='waiting';p.error=null;await Offline.putPending(p);renderPending();renderNet();syncPending()});
 $$('[data-pdiscard]').forEach(b=>b.onclick=async()=>{
  const p=find(b.dataset.pdiscard);if(!p)return;
  if(!confirm(`Discard offline sale ${p.localNo} (${money(p.display.total)})?\n\nIt will NOT be recorded: stock and the Cash Balance will not change. Only do this if the sale did not really happen, or you will record it by hand.`))return;
  await Offline.removePending(p.clientId);renderPending();renderNet();
 });
}
let syncing=false,lastAuthToast=0;
async function doSync(){
 if(syncing)return;syncing=true;
 let uploaded=0,rejected=0;
 try{
  for(const p of Offline.listPending().slice()){
   if(p.status==='failed')continue;
   try{
    await api('/api/sales',{method:'POST',noAuthRedirect:true,body:JSON.stringify({clientId:p.clientId,soldAt:p.soldAt,customerName:p.customerName,note:p.note,items:p.items})});
    await Offline.removePending(p.clientId);
    Offline.decrementCatalog(p.items).catch(()=>{});
    uploaded++;
   }catch(e){
    if(e.offline)break;
    if(e.auth){if(Date.now()-lastAuthToast>300000){lastAuthToast=Date.now();toast(e.message)}break}
    if(e.status>=500)break;                                  // server trouble: try again later
    p.status='failed';p.error=e.message;await Offline.putPending(p);rejected++;   // the server refused it (e.g. not enough stock)
   }
  }
 }finally{syncing=false}
 renderPending();renderNet();
 if(uploaded){toast(`${uploaded} offline sale(s) uploaded.`);refreshCatalog();if(!$('#page-sales').classList.contains('hidden'))loadSalesHistory().catch(()=>{})}
 if(rejected)toast(`${rejected} offline sale(s) were not accepted. See the list on the Sales page.`);
}
const syncPending=()=>navigator.locks?navigator.locks.request('inventory-sync',{ifAvailable:true},lock=>lock?doSync():null):doSync();
$('#syncNow').onclick=()=>{heartbeat()};
async function refreshCatalog(){try{await Offline.putCatalog(await api('/api/products/catalog'))}catch{}}
async function heartbeat(){
 try{await api('/api/me',{noAuthRedirect:true})}catch{}
 if(netUp&&Offline.listPending().length)await syncPending();
}
window.addEventListener('online',()=>{renderNet();heartbeat()});
window.addEventListener('offline',()=>setNet(false));
setInterval(()=>{if(!netUp||Offline.listPending().some(p=>p.status!=='failed'))heartbeat()},20000);
setInterval(()=>{if(netUp)refreshCatalog()},300000);
if('serviceWorker' in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});

// ---------- receipt
function printReceipt(sale){
 const s=settings;
 $('#receipt').innerHTML=`<div class="r-center">${s.logoData?`<img src="${esc(s.logoData)}" alt="">`:''}<b>${esc(s.businessName)}</b><br>${esc(s.address||'')}${s.phone?'<br>Tel: '+esc(s.phone):''}</div><hr>
 <div>Receipt: ${esc(sale.receiptNo)}${sale.provisional?' (made offline, not yet uploaded)':''}<br>Date: ${esc(dateText(sale.createdAt))}${sale.customerName?'<br>Customer: '+esc(sale.customerName):''}</div><hr>
 <table><thead><tr><th>Item</th><th class="r-right">Qty</th><th class="r-right">Price</th><th class="r-right">Total</th></tr></thead><tbody>
 ${sale.items.map(i=>`<tr><td>${esc(i.name)}</td><td class="r-right">${i.quantity}</td><td class="r-right">${money(i.unitPrice)}</td><td class="r-right">${money(i.lineTotal)}</td></tr>`).join('')}
 </tbody></table><hr><table><tr class="r-total"><td>TOTAL</td><td class="r-right">${money(sale.total)}</td></tr></table><hr><div class="r-center">Thank you for your purchase!</div>`;
 document.body.classList.add('printing-receipt');
 window.addEventListener('afterprint',()=>document.body.classList.remove('printing-receipt'),{once:true});
 setTimeout(()=>window.print(),150);
}

// ---------- sales records (paged)
const itemsText=sale=>sale.items.map(i=>`${esc(i.name)} ×${i.quantity}`).join(', ');
const salesRange=makeRange('#salesRange',['today','week','month','year','custom'],'today',()=>{pg.sales=0;loadSalesHistory().catch(fail)});
async function loadSalesHistory(){
 if(!salesRange.ready())return;
 let r;
 try{r=await api(`/api/sales?${salesRange.query()}&page=${pg.sales}&size=${PAGE}`)}
 catch(e){
  if(e.offline){$('#salesTable').innerHTML='<tr><td colspan="7" class="empty">You are offline. Sales records need an internet connection. You can still sell: sales are saved on this device and upload later.</td></tr>';return}
  throw e;
 }
 if(!r.sales.items.length&&pg.sales>0){pg.sales=Math.max(0,r.sales.totalPages-1);return loadSalesHistory()}
 salesRows=r.sales.items;
 $('#hCount').textContent=r.count.toLocaleString();$('#hRevenue').textContent=money(r.revenue);$('#hProfit').textContent=money(r.profit);
 $('#hLabel').textContent=`${r.label}: ${r.from} to ${r.to}`;
 $('#salesTable').innerHTML=salesRows.map(s=>`<tr><td>${esc(s.receiptNo)}</td><td>${dateText(s.createdAt)}</td><td class="items-cell">${itemsText(s)}</td><td>${esc(s.customerName||'—')}</td><td>${money(s.total)}</td><td>${money(s.profit)}</td><td><button class="btn secondary small-btn" data-receipt="${s.id}" type="button">Receipt</button></td></tr>`).join('')||'<tr><td colspan="7" class="empty">No sales in this period.</td></tr>';
 $$('[data-receipt]').forEach(b=>b.onclick=()=>{const s=salesRows.find(x=>x.id===Number(b.dataset.receipt));if(s)printReceipt(s)});
 renderPager('#salesPager',r.sales,p=>{pg.sales=p;loadSalesHistory().catch(fail)});
}

// ---------- settings: business profile
let pendingLogo=null,removeLogo=false;
function showLogoPreview(src){const img=$('#logoPreview');if(src){img.src=src;img.classList.remove('hidden')}else{img.removeAttribute('src');img.classList.add('hidden')}}
function loadSettingsForm(){
 const f=$('#settingsForm');f.elements.businessName.value=settings.businessName||'';f.elements.phone.value=settings.phone||'';f.elements.address.value=settings.address||'';
 pendingLogo=null;removeLogo=false;showLogoPreview(settings.logoData);$('#logoFile').value='';
}
$('#logoFile').onchange=e=>{
 const file=e.target.files[0];if(!file)return;
 const reader=new FileReader();
 reader.onload=()=>{
  const img=new Image();
  img.onload=()=>{
   const max=320,scale=Math.min(1,max/Math.max(img.width,img.height));
   const c=document.createElement('canvas');c.width=Math.round(img.width*scale);c.height=Math.round(img.height*scale);
   c.getContext('2d').drawImage(img,0,0,c.width,c.height);
   pendingLogo=c.toDataURL('image/png');removeLogo=false;showLogoPreview(pendingLogo);
  };
  img.onerror=()=>toast('That file is not a readable image.');
  img.src=reader.result;
 };
 reader.readAsDataURL(file);
};
$('#removeLogo').onclick=()=>{pendingLogo=null;removeLogo=true;showLogoPreview(null);$('#logoFile').value=''};
$('#settingsForm').onsubmit=async e=>{
 e.preventDefault();const f=e.target;
 try{
  settings=await api('/api/settings',{method:'PUT',body:JSON.stringify({businessName:f.elements.businessName.value,phone:f.elements.phone.value,address:f.elements.address.value,logoData:pendingLogo,removeLogo})});
  applyBranding();loadSettingsForm();toast('Settings saved');
 }catch(err){toast(err.message)}
};

// ---------- settings: users and roles (admin)
const roleOptions=sel=>[['ADMIN','Inventory Admin'],['ACCOUNTANT','Accountant']].map(([v,l])=>`<option value="${v}" ${v===sel?'selected':''}>${l}</option>`).join('');
async function loadUsers(){
 usersList=await api('/api/users');
 $('#usersTable').innerHTML=usersList.map(u=>`<tr><td>${esc(u.username)}${u.username.toLowerCase()===me.username.toLowerCase()?' <small class="muted">(you)</small>':''}</td><td><select data-role="${u.id}">${roleOptions(u.role)}</select></td><td><span class="pill ${u.enabled?'':'critical'}">${u.enabled?'Active':'Disabled'}</span></td><td><button class="btn secondary small-btn" data-toggle="${u.id}" type="button">${u.enabled?'Disable':'Enable'}</button> <button class="btn secondary small-btn" data-pw="${u.id}" type="button">Reset password</button> <button class="btn danger small-btn" data-deluser="${u.id}" type="button">Delete</button></td></tr>`).join('');
 const change=async(id,body,msg)=>{try{await api('/api/users/'+id,{method:'PUT',body:JSON.stringify(body)});toast(msg)}catch(e){toast(e.message)}await loadUsers()};
 $$('[data-role]').forEach(s=>s.onchange=()=>change(s.dataset.role,{role:s.value},'Role updated'));
 $$('[data-toggle]').forEach(b=>b.onclick=()=>{const u=usersList.find(x=>x.id===Number(b.dataset.toggle));change(u.id,{enabled:!u.enabled},u.enabled?'User disabled':'User enabled')});
 $$('[data-pw]').forEach(b=>b.onclick=()=>{const u=usersList.find(x=>x.id===Number(b.dataset.pw));const pw=prompt(`New password for ${u.username} (at least 8 characters):`);if(pw)change(u.id,{password:pw},'Password changed')});
 $$('[data-deluser]').forEach(b=>b.onclick=async()=>{
  const u=usersList.find(x=>x.id===Number(b.dataset.deluser));
  if(!confirm(`Delete user ${u.username}?`))return;
  try{await api('/api/users/'+u.id,{method:'DELETE'});toast('User deleted')}catch(e){toast(e.message)}
  await loadUsers();
 });
}
$('#userForm').onsubmit=async e=>{
 e.preventDefault();const d=Object.fromEntries(new FormData(e.target));
 try{await api('/api/users',{method:'POST',body:JSON.stringify(d)});e.target.reset();toast('User added');await loadUsers()}catch(err){toast(err.message)}
};

// ---------- settings: delete a sale (admin, paged)
const settingsRange=makeRange('#settingsRange',['today','week','month','year','custom'],'today',()=>{pg.deletable=0;loadDeletable().catch(fail)});
async function loadDeletable(){
 if(!settingsRange.ready())return;
 const r=await api(`/api/sales?${settingsRange.query()}&page=${pg.deletable}&size=${PAGE}`);
 if(!r.sales.items.length&&pg.deletable>0){pg.deletable=Math.max(0,r.sales.totalPages-1);return loadDeletable()}
 salesRows=r.sales.items;
 $('#deletableTable').innerHTML=salesRows.map(s=>`<tr><td>${esc(s.receiptNo)}</td><td>${dateText(s.createdAt)}</td><td class="items-cell">${itemsText(s)}</td><td>${money(s.total)}</td><td><button class="btn danger small-btn" data-delete-sale="${s.id}" type="button">Delete &amp; restore</button></td></tr>`).join('')||'<tr><td colspan="5" class="empty">No sales in this period.</td></tr>';
 $$('[data-delete-sale]').forEach(b=>b.onclick=()=>deleteSale(Number(b.dataset.deleteSale)));
 renderPager('#deletablePager',r.sales,p=>{pg.deletable=p;loadDeletable().catch(fail)});
}
async function deleteSale(id){
 const s=salesRows.find(x=>x.id===id);if(!s)return;
 const units=s.items.reduce((n,i)=>n+i.quantity,0);
 const cashPart=` and ${money(s.total)} will be taken out of the cash balance`;
 if(!confirm(`Delete sale ${s.receiptNo}?\n\n${units} unit(s) will go back into stock${cashPart}.`))return;
 try{await api('/api/sales/'+id,{method:'DELETE'});toast(`Sale ${s.receiptNo} deleted. Stock and cash restored.`);await loadDeletable()}catch(err){toast(err.message)}
}

// ---------- settings: clear all data (admin)
$('#openClear').onclick=()=>{$('#clearForm').reset();$('#clearModal').classList.remove('hidden')};
const closeClear=()=>$('#clearModal').classList.add('hidden');
$('#closeClear').onclick=closeClear;$('#cancelClear').onclick=closeClear;
$('#clearForm').onsubmit=async e=>{
 e.preventDefault();const d=Object.fromEntries(new FormData(e.target));
 try{
  await api('/api/admin/clear-data',{method:'POST',body:JSON.stringify(d)});
  closeClear();cart=[];renderCart();Object.keys(pg).forEach(k=>pg[k]=0);
  toast('All data has been erased.');go('dashboard');
 }catch(err){toast(err.message)}
};

// ---------- reports (client-side paging of the product table)
const reportRange=makeRange('#reportRange',['all','today','week','month','year','custom'],'all',()=>{pg.report=0;loadReport().catch(fail)});
function renderReportTable(){
 const rows=reportData.products,total=rows.length,start=pg.report*PAGE;
 $('#reportTable').innerHTML=rows.slice(start,start+PAGE).map(p=>`<tr><td><b>${esc(p.name)}</b></td><td>${esc(p.sku)}</td><td>${esc(p.category)}</td><td>${p.quantity}</td><td>${money(p.costPrice)}</td><td>${money(p.sellingPrice)}</td><td>${money(p.stockValue)}</td><td>${money(p.expectedValue)}</td><td>${money(p.expectedProfit)}</td><td>${p.unitsSold}</td><td>${money(p.profit)}</td></tr>`).join('')||'<tr><td colspan="11" class="empty">No products yet.</td></tr>';
 renderPager('#reportPager',{page:pg.report,size:PAGE,totalElements:total,totalPages:Math.ceil(total/PAGE)},p=>{pg.report=p;renderReportTable()});
}
async function loadReport(){
 if(!reportRange.ready())return;
 const r=await api('/api/reports?'+reportRange.query());reportData=r;
 $('#reportLabel').textContent=`Profit and sales shown for: ${r.label} (${r.from} to ${r.to}). Stock values are for the stock you hold right now.`;
 $('#rValue').textContent=money(r.totalProductsValue);$('#rUnits').textContent=`${r.unitsInStock.toLocaleString()} units in ${r.productCount.toLocaleString()} products`;
 $('#rExpected').textContent=money(r.expectedProductValue);$('#rExpProfit').textContent='Expected profit '+money(r.expectedProfit);
 $('#rProfit').textContent=money(r.salesProfit);$('#rSalesLine').textContent=`${r.salesCount} sale(s), ${r.unitsSold} unit(s) sold`;
 $('#rRevenue').textContent=money(r.salesRevenue);$('#rCost').textContent='Cost of goods '+money(r.salesCost);
 if(pg.report*PAGE>=r.products.length)pg.report=0;
 renderReportTable();
}
$('#exportReport').onclick=()=>{if(!reportRange.ready()){toast('Choose both a start date and an end date.');return}location.href='/api/reports/export?'+reportRange.query()};

// ---------- page loader
async function loadPage(page){
 try{
  if(page==='dashboard'){await loadDashboard();await loadRecentCash()}
  if(page==='products'){await loadProducts()}
  if(page==='sales'){await loadBranding();renderCart();renderPending();await loadSalesHistory()}
  if(page==='purchases'){await loadProductOptions();await loadPurchases()}
  if(page==='cash'){await loadCash()}
  if(page==='reports'){await loadReport()}
  if(page==='settings'){await loadBranding();loadSettingsForm();await Promise.all([loadUsers(),loadDeletable()])}
 }catch(e){toast(e.message)}
}
$('#logoutBtn').onclick=async()=>{
 const n=Offline.listPending().length;
 if(n&&!confirm(`${n} offline sale(s) have not uploaded yet. They stay safely on this device and upload the next time someone signs in with internet. Sign out anyway?`))return;
 try{await fetch('/logout',{method:'POST',credentials:'same-origin',headers:{'X-XSRF-TOKEN':csrf()}})}
 catch{toast('You are offline, so signing out is not possible right now.');return}
 try{await Offline.deleteMeta('me');await Offline.deleteMeta('settings');await Offline.clearCatalog()}catch{}
 location.href='/login.html';
};
renderCart();
(async()=>{
 await Offline.reloadPending().catch(()=>{});
 try{me=await api('/api/me');Offline.setMeta('me',me).catch(()=>{})}
 catch(e){
  if(!e.offline){toast(e.message);return}
  const saved=await Offline.getMeta('me').catch(()=>null);
  if(!saved){toast('You are offline and have not signed in on this device yet. Connect to the internet and sign in once.');return}
  me=saved;
 }
 applyRole();await loadBranding();renderPending();renderNet();
 go(isAdmin()&&netUp?'dashboard':'sales');
 if(netUp){refreshCatalog();syncPending()}
})();
